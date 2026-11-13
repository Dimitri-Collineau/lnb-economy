package fr.bde_teknantes.lnb.economy.poker

import fr.bde_teknantes.lnb.economy.ModContent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent

/**
 * With MineVegas Casino installed, this mod's poker table takes the place of MineVegas's: MineVegas's recipe makes
 * ours (see `data/roulettecasino/recipe/poker_table.json`), its table is in no creative tab, and one that a player
 * holds turns into ours. A MineVegas table already placed no longer opens: broken, it drops an item that turns into
 * ours too. Only touches MineVegas by its IDs, so its classes never load from here.
 */
object MineVegasPokerTable {
    private val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("roulettecasino", "poker_table")

    /** How often a player's inventory is looked through, in ticks. */
    private const val SCAN_TICKS = 20

    private val item get() = BuiltInRegistries.ITEM.get(ID).takeIf { it != Items.AIR }
    private val block get() = BuiltInRegistries.BLOCK.getOptional(ID).orElse(null)

    fun onBuildTabs(event: BuildCreativeModeTabContentsEvent) {
        val theirs = item ?: return
        for (stack in event.parentEntries.filter { it.`is`(theirs) }) event.remove(stack, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY)
        for (stack in event.searchEntries.filter { it.`is`(theirs) }) event.remove(stack, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY)
    }

    fun onPlayerTick(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        if (player.tickCount % SCAN_TICKS != 0) return
        val theirs = item ?: return
        val inventory = player.inventory
        var swapped = false
        for (slot in 0 until inventory.containerSize) {
            val stack = inventory.getItem(slot)
            if (!stack.`is`(theirs)) continue
            inventory.setItem(slot, ItemStack(ModContent.POKER_TABLE_ITEM.get(), stack.count))
            swapped = true
        }
        if (player.containerMenu.carried.`is`(theirs)) {
            player.containerMenu.carried = ItemStack(ModContent.POKER_TABLE_ITEM.get(), player.containerMenu.carried.count)
            swapped = true
        }
        if (swapped) {
            inventory.setChanged()
            player.displayClientMessage(Component.translatable("economy.lnbeconomy.poker.swapped"), true)
        }
    }

    /** A MineVegas table placed before stays as it is, but no longer opens. */
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        val theirs = block ?: return
        if (!event.level.getBlockState(event.pos).`is`(theirs)) return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.sidedSuccess(event.level.isClientSide)
        if (!event.level.isClientSide) event.entity.displayClientMessage(Component.translatable("economy.lnbeconomy.poker.retired"), true)
    }
}
