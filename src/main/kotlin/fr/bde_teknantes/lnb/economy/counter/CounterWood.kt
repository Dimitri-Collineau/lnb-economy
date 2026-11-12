package fr.bde_teknantes.lnb.economy.counter

import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

/**
 * The planks a counter's wall is made of: every vanilla wood, so the counter matches the build around it.
 * The wall model uses the vanilla planks texture of the chosen wood (see `models/block/counter_<wood>.json`).
 */
enum class CounterWood(private val id: String, val planks: () -> Item) : StringRepresentable {
    OAK("oak", { Items.OAK_PLANKS }),
    SPRUCE("spruce", { Items.SPRUCE_PLANKS }),
    BIRCH("birch", { Items.BIRCH_PLANKS }),
    JUNGLE("jungle", { Items.JUNGLE_PLANKS }),
    ACACIA("acacia", { Items.ACACIA_PLANKS }),
    DARK_OAK("dark_oak", { Items.DARK_OAK_PLANKS }),
    MANGROVE("mangrove", { Items.MANGROVE_PLANKS }),
    CHERRY("cherry", { Items.CHERRY_PLANKS }),
    BAMBOO("bamboo", { Items.BAMBOO_PLANKS }),
    CRIMSON("crimson", { Items.CRIMSON_PLANKS }),
    WARPED("warped", { Items.WARPED_PLANKS });

    override fun getSerializedName(): String = id

    companion object {
        val DEFAULT = DARK_OAK

        fun byId(id: String): CounterWood? = entries.firstOrNull { it.id == id }
    }
}
