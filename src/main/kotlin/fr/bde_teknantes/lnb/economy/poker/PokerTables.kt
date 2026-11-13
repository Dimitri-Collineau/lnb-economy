package fr.bde_teknantes.lnb.economy.poker

import fr.bde_teknantes.lnb.economy.network.PokerActionPayload
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/** What players ask of poker tables: opening one, and the requests of its screen. */
object PokerTables {
    private const val REACH = 8.0

    fun open(player: ServerPlayer, pos: BlockPos) {
        tableAt(player, pos)?.open(player)
    }

    fun handleAction(payload: PokerActionPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        // Leaving the screen and deciding work from anywhere: a seated player who stepped back still plays.
        val table = player.serverLevel().takeIf { it.isLoaded(payload.pos) }?.getBlockEntity(payload.pos) as? PokerTableBlockEntity ?: return
        when (payload.action) {
            PokerActionPayload.SIT -> if (inReach(player, payload.pos)) table.sit(player, payload.seat, payload.ante)
            PokerActionPayload.CALL -> table.call(player)
            PokerActionPayload.FOLD -> table.fold(player)
            PokerActionPayload.CLOSE -> table.close(player)
        }
    }

    private fun tableAt(player: ServerPlayer, pos: BlockPos): PokerTableBlockEntity? =
        if (inReach(player, pos)) player.serverLevel().getBlockEntity(pos) as? PokerTableBlockEntity else null

    private fun inReach(player: ServerPlayer, pos: BlockPos): Boolean = player.distanceToSqr(pos.center) <= REACH * REACH
}
