package fr.bde_teknantes.lnb.economy.chip

import fr.bde_teknantes.lnb.economy.ModContent
import fr.bde_teknantes.lnb.economy.api.Handles
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.api.distmarker.Dist
import fr.bde_teknantes.lnb.economy.money.Amounts

/**
 * A casino chip worth [value] LNBits at the casino in its [ModContent.CASINO] component, backed by that casino's
 * account. Only that casino's machines take it and only its counters cash it.
 */
class ChipItem(val value: Int, properties: Properties) : Item(properties) {
    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        val casino = Chips.casinoOf(stack)
        if (casino == null) {
            tooltip.add(Component.translatable("item.lnbeconomy.chip.blank").withStyle(ChatFormatting.RED))
            return
        }
        tooltip.add(Component.translatable("item.lnbeconomy.chip.casino", Handles.display(casino)).withStyle(ChatFormatting.GOLD))
        if (stack.count > 1) {
            val language = if (FMLEnvironment.dist == Dist.CLIENT) Minecraft.getInstance().options.languageCode else "en_us"
            tooltip.add(
                Component.translatable("item.lnbeconomy.chip.stack", Amounts.format(Chips.valueOf(stack), language))
                    .withStyle(ChatFormatting.GRAY),
            )
        }
    }

    /** A chip item burnt in lava or fire, or blown up: the casino no longer owes it. */
    override fun onDestroyed(itemEntity: ItemEntity) {
        LostChips.destroyed(itemEntity)
    }
}
