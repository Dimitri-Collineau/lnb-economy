package fr.bde_teknantes.lnb.economy.trade

import fr.bde_teknantes.lnb.economy.network.TradeStatePayload
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID

/**
 * A trade between two players, from the moment both accept until it runs or one leaves. Each side has an offer
 * (copies of items, see [TradeMenu]), an amount of LNBits and the account it trades with. Any change sets both sides
 * back to not ready; once both are ready, a countdown runs, then [Trades.execute] takes over.
 */
class TradeSession(val server: MinecraftServer, val first: UUID, val second: UUID) {
    /** One side's offer. */
    inner class Side(val player: UUID) {
        val items = SimpleContainer(TradeMenu.SLOTS).apply { addListener { changed() } }
        var amount = 0L
        var accountId: String? = null
        var ready = false

        val offer: List<ItemStack> get() = (0 until items.containerSize).map { items.getItem(it).copy() }
    }

    val firstSide = Side(first)
    val secondSide = Side(second)

    /** Ticks left before the trade runs, or -1 while a side isn't ready. */
    var countdown = -1
        private set

    var isOpen = true
        private set

    fun sideOf(player: UUID): Side? = when (player) {
        first -> firstSide
        second -> secondSide
        else -> null
    }

    fun otherOf(player: UUID): Side? = when (player) {
        first -> secondSide
        second -> firstSide
        else -> null
    }

    /** Opens the trade screen for both players. */
    fun open() {
        for (side in listOf(firstSide, secondSide)) {
            val player = server.playerList.getPlayer(side.player) ?: continue
            val other = otherOf(side.player) ?: continue
            val partner = server.playerList.getPlayer(other.player)?.gameProfile?.name.orEmpty()
            player.openMenu(
                SimpleMenuProvider(
                    { id, inventory, _ -> TradeMenu(id, inventory, side.items, other.items, partner, this) },
                    Component.translatable("gui.lnbeconomy.trade.title", partner),
                ),
            ) { buf -> buf.writeUtf(partner, 16) }
        }
        broadcast()
    }

    /** Something changed on one side: nobody is ready any more. */
    fun changed() {
        if (!isOpen) return
        firstSide.ready = false
        secondSide.ready = false
        countdown = -1
        broadcast()
    }

    fun setMoney(player: UUID, amount: Long, accountId: String?) {
        val side = sideOf(player) ?: return
        if (side.amount == amount && side.accountId == accountId) return
        side.amount = amount
        side.accountId = accountId
        changed()
    }

    fun setReady(player: UUID, ready: Boolean) {
        val side = sideOf(player) ?: return
        if (!isOpen || side.ready == ready) return
        side.ready = ready
        countdown = if (firstSide.ready && secondSide.ready) COUNTDOWN_TICKS else -1
        broadcast()
    }

    /** Counts down once both are ready. True when the trade must run now. */
    fun tick(): Boolean {
        if (!isOpen || countdown < 0) return false
        if (countdown % 20 == 0) broadcast()
        return --countdown < 0
    }

    /** One player closed the screen or left: the trade is off for both. */
    fun onClosed(player: Player) {
        if (!isOpen) return
        Trades.cancel(this, player.uuid)
    }

    /** Ends the session and closes both screens. Nothing was taken from anyone, so there is nothing to give back. */
    fun close() {
        if (!isOpen) return
        isOpen = false
        for (uuid in listOf(first, second)) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            if (player.containerMenu is TradeMenu) player.closeContainer()
        }
    }

    private fun broadcast() {
        for (side in listOf(firstSide, secondSide)) {
            val player: ServerPlayer = server.playerList.getPlayer(side.player) ?: continue
            val other = otherOf(side.player) ?: continue
            PacketDistributor.sendToPlayer(
                player,
                TradeStatePayload(side.amount, side.accountId, other.amount, side.ready, other.ready, if (countdown < 0) -1 else (countdown + 19) / 20),
            )
        }
    }

    companion object {
        const val COUNTDOWN_TICKS = 60
    }
}
