package fr.bde_teknantes.lnb.economy.market

import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.api.EconomyApi
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.crafting.Ingredient

/**
 * Sends every recipe of the game to the site, so its loop validator knows what can be crafted from what. Done at
 * start, at every reconnection of the event stream and after a data-pack reload, whether or not the Market is on:
 * without the graph an admin can't enable or reprice items. A graph identical to the last one sent is not sent again.
 */
object MarketRecipes {
    private var lastSent: Int? = null

    /** Server thread only. */
    fun send(server: MinecraftServer, force: Boolean = false) {
        val graph = RecipeGraph.build(read(server))
        if (graph.isEmpty()) return
        val hash = graph.hashCode()
        if (!force && hash == lastSent) return
        lastSent = hash
        EconomyApi.putRecipes(MarketRecipeBody(graph)).thenAccept { result ->
            server.execute {
                when (result) {
                    is ApiResult.Ok -> {
                        LnbEconomy.LOGGER.info("Sent {} recipes to the Market ({} stored{})", graph.size, result.body.stored, if (result.body.changed) ", changed" else "")
                        for (problem in result.body.problems) {
                            LnbEconomy.LOGGER.warn("Market recipe loop: {} is worth {} but can be crafted for {}", problem.item, problem.value, problem.cost)
                        }
                    }
                    else -> {
                        // Tried again at the next refresh.
                        lastSent = null
                        LnbEconomy.LOGGER.warn("Couldn't send the recipes to the Market: {}", result.describe())
                    }
                }
            }
        }
    }

    fun forget() {
        lastSent = null
    }

    private fun read(server: MinecraftServer): List<RecipeGraph.Raw> {
        val access = server.registryAccess()
        val out = ArrayList<RecipeGraph.Raw>()
        for (holder in server.recipeManager.recipes) {
            val recipe = holder.value()
            val result = try {
                recipe.getResultItem(access)
            } catch (e: Exception) {
                continue
            }
            if (result.isEmpty) continue
            val slots = recipe.ingredients.filterNot(Ingredient::isEmpty).map { ingredient ->
                ingredient.items.map { BuiltInRegistries.ITEM.getKey(it.item).toString() }.distinct()
            }
            out += RecipeGraph.Raw(
                BuiltInRegistries.RECIPE_TYPE.getKey(recipe.type).toString(),
                BuiltInRegistries.ITEM.getKey(result.item).toString(),
                result.count,
                slots,
            )
        }
        return out
    }
}
