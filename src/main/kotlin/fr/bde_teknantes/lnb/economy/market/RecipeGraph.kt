package fr.bde_teknantes.lnb.economy.market

/**
 * Turns the game's recipes into the graph `PUT /recipes` wants (section 4.5 of the site's `market-mod.md`), without
 * Minecraft: [Raw] is a recipe as the game reports it, slot by slot.
 */
object RecipeGraph {
    const val MAX_RECIPES = 100_000
    const val MAX_INPUTS = 27
    const val MAX_COUNT = 1000

    /** [slots]: for each ingredient slot, the items it accepts (several for a tag). Empty slots are left out already. */
    data class Raw(val type: String, val output: String, val outputCount: Int, val slots: List<List<String>>)

    /**
     * Repeated ingredients merge into one input with a count. A slot that accepts several items gives one recipe per
     * item it matches; with several such slots, the n-th recipe takes each slot's n-th item (wrapping), so the number
     * of recipes stays the size of the largest tag instead of the product of them all.
     */
    fun build(raw: Collection<Raw>): List<MarketRecipe> {
        val recipes = LinkedHashSet<MarketRecipe>()
        for (recipe in raw) {
            if (recipe.slots.isEmpty() || recipe.output.isEmpty() || recipe.outputCount < 1) continue
            if (recipe.slots.any { it.isEmpty() }) continue
            val variants = recipe.slots.maxOf { it.size }
            for (variant in 0 until variants) {
                val counts = LinkedHashMap<String, Int>()
                for (slot in recipe.slots) counts.merge(slot[variant % slot.size], 1, Int::plus)
                if (counts.size > MAX_INPUTS) continue
                recipes += MarketRecipe(
                    recipe.type.take(100),
                    RecipeStack(recipe.output, recipe.outputCount.coerceAtMost(MAX_COUNT)),
                    counts.map { (id, count) -> RecipeStack(id, count.coerceAtMost(MAX_COUNT)) },
                )
                if (recipes.size >= MAX_RECIPES) return recipes.toList()
            }
        }
        return recipes.toList()
    }
}
