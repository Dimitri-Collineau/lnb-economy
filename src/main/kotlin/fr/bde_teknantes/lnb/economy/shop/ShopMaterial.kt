package fr.bde_teknantes.lnb.economy.shop

import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

/**
 * What a shop's pedestal is made of: every vanilla wood and a few polished stones, so the shop matches the build around
 * it. The pedestal model uses the vanilla texture [texture] (see `models/block/shop_<material>_<half>.json`); the brass,
 * the felt and the glass stay the same.
 */
enum class ShopMaterial(private val id: String, val texture: String, val item: () -> Item) : StringRepresentable {
    OAK("oak", "oak_planks", { Items.OAK_PLANKS }),
    SPRUCE("spruce", "spruce_planks", { Items.SPRUCE_PLANKS }),
    BIRCH("birch", "birch_planks", { Items.BIRCH_PLANKS }),
    JUNGLE("jungle", "jungle_planks", { Items.JUNGLE_PLANKS }),
    ACACIA("acacia", "acacia_planks", { Items.ACACIA_PLANKS }),
    DARK_OAK("dark_oak", "dark_oak_planks", { Items.DARK_OAK_PLANKS }),
    MANGROVE("mangrove", "mangrove_planks", { Items.MANGROVE_PLANKS }),
    CHERRY("cherry", "cherry_planks", { Items.CHERRY_PLANKS }),
    BAMBOO("bamboo", "bamboo_planks", { Items.BAMBOO_PLANKS }),
    CRIMSON("crimson", "crimson_planks", { Items.CRIMSON_PLANKS }),
    WARPED("warped", "warped_planks", { Items.WARPED_PLANKS }),
    QUARTZ("quartz", "quartz_block_side", { Items.QUARTZ_BLOCK }),
    CALCITE("calcite", "calcite", { Items.CALCITE }),
    DIORITE("diorite", "polished_diorite", { Items.POLISHED_DIORITE }),
    ANDESITE("andesite", "polished_andesite", { Items.POLISHED_ANDESITE }),
    GRANITE("granite", "polished_granite", { Items.POLISHED_GRANITE }),
    DEEPSLATE("deepslate", "polished_deepslate", { Items.POLISHED_DEEPSLATE }),
    BLACKSTONE("blackstone", "polished_blackstone", { Items.POLISHED_BLACKSTONE }),
    SMOOTH_STONE("smooth_stone", "smooth_stone", { Items.SMOOTH_STONE }),
    SANDSTONE("sandstone", "sandstone_top", { Items.SMOOTH_SANDSTONE }),
    RED_SANDSTONE("red_sandstone", "red_sandstone_top", { Items.SMOOTH_RED_SANDSTONE }),
    PURPUR("purpur", "purpur_block", { Items.PURPUR_BLOCK }),
    DARK_PRISMARINE("dark_prismarine", "dark_prismarine", { Items.DARK_PRISMARINE });

    override fun getSerializedName(): String = id

    companion object {
        val DEFAULT = DARK_OAK

        fun byId(id: String): ShopMaterial? = entries.firstOrNull { it.id == id }
    }
}
