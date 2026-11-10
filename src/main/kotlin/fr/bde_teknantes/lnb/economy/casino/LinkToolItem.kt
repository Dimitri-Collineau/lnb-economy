package fr.bde_teknantes.lnb.economy.casino

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.UseOnContext

/** The casino link wand: right click a casino machine or a counter to choose its casino. */
class LinkToolItem(properties: Properties) : Item(properties) {
    /** Runs before the block's own right click, so machines don't open their menu. */
    override fun onItemUseFirst(stack: ItemStack, context: UseOnContext): InteractionResult {
        val level = context.level
        val target = Links.targetAt(level, context.clickedPos) ?: return InteractionResult.PASS
        val player = context.player as? ServerPlayer
        if (!level.isClientSide && player != null) Links.open(player, target)
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        tooltip.add(Component.translatable("item.lnbeconomy.casino_link_tool.usage").withStyle(ChatFormatting.GRAY))
    }
}
