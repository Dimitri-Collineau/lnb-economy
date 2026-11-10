package fr.bde_teknantes.lnb.economy.counter

import fr.bde_teknantes.lnb.economy.ModContent
import fr.bde_teknantes.lnb.economy.account.PlayerAccounts
import fr.bde_teknantes.lnb.economy.api.Handles
import fr.bde_teknantes.lnb.economy.api.Party
import fr.bde_teknantes.lnb.economy.api.Problems
import fr.bde_teknantes.lnb.economy.api.TransferBody
import fr.bde_teknantes.lnb.economy.casino.CasinoBook
import fr.bde_teknantes.lnb.economy.casino.CasinoLinks
import fr.bde_teknantes.lnb.economy.casino.Casinos
import fr.bde_teknantes.lnb.economy.chip.ChipLedger
import fr.bde_teknantes.lnb.economy.chip.ChipMailbox
import fr.bde_teknantes.lnb.economy.chip.Chips
import fr.bde_teknantes.lnb.economy.chip.Denominations
import fr.bde_teknantes.lnb.economy.ledger.JournalEntry
import fr.bde_teknantes.lnb.economy.ledger.Outcome
import fr.bde_teknantes.lnb.economy.ledger.TransferJournal
import fr.bde_teknantes.lnb.economy.money.Texts
import fr.bde_teknantes.lnb.economy.network.CounterBuyPayload
import fr.bde_teknantes.lnb.economy.network.CounterCashPayload
import fr.bde_teknantes.lnb.economy.network.CounterChangePayload
import fr.bde_teknantes.lnb.economy.network.EconomyStatus
import fr.bde_teknantes.lnb.economy.network.OpenCounterPayload
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/**
 * What counters do. Buying and cashing are the only casino moves that reach the site:
 * buying pays the casino and makes chips, cashing destroys chips and the casino pays the player.
 */
object Counters {
    private const val BUY = "counterBuy"
    private const val CASH = "counterCash"
    private const val REACH = 8.0

    private const val MEMO_BUY = "Chips bought at the counter"
    private const val MEMO_CASH = "Chips cashed at the counter"

    fun register() {
        TransferJournal.register(BUY, ::finishBuy)
        TransferJournal.register(CASH, ::finishCash)
    }

    fun open(player: ServerPlayer, pos: BlockPos) {
        val link = CasinoLinks.get(player.server).get(player.level().dimension(), pos)
        if (link == null) {
            player.displayClientMessage(Component.translatable("economy.lnbeconomy.counter.not_linked"), true)
            return
        }
        PlayerAccounts.refresh(player.server, player.uuid, force = false)
        Casinos.refresh(player.server, link.handle)
        PacketDistributor.sendToPlayer(player, OpenCounterPayload(pos, link.handle, Casinos.name(link.handle)))
    }

    fun handleBuy(payload: CounterBuyPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        val handle = casinoAt(player, payload.pos) ?: return
        if (payload.amount < 1 || payload.amount > TransferBody.MAX_AMOUNT) return
        if (!hasEconomy(player) || !casinoOpen(player, handle)) return
        val accounts = PlayerAccounts.accounts(player.uuid)
        if (payload.accountId != null && accounts?.accounts?.any { it.id == payload.accountId && it.canSpend } != true) {
            error(player, Component.translatable("economy.lnbeconomy.problem.notAllowed"))
            return
        }
        TransferJournal.submit(
            from = Party(player = player.uuid.toString(), account = payload.accountId),
            to = Party(account = handle),
            amount = payload.amount,
            kind = TransferBody.KIND_CASINO,
            memo = MEMO_BUY,
            purpose = BUY,
            data = mapOf("player" to player.uuid.toString(), "casino" to handle),
        )
    }

    fun handleCash(payload: CounterCashPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        val handle = casinoAt(player, payload.pos) ?: return
        if (!hasEconomy(player) || !casinoOpen(player, handle)) return
        val value = Chips.takeAll(player.inventory, handle)
        if (value == 0L) {
            error(player, Component.translatable("economy.lnbeconomy.counter.no_chips", Handles.display(handle)))
            return
        }
        // The chips are gone from now on; they come back if the site refuses.
        ChipLedger.get(player.server).add(handle, -value)
        TransferJournal.submit(
            from = Party(account = handle),
            to = Party(player = player.uuid.toString()),
            amount = value,
            kind = TransferBody.KIND_CASINO,
            memo = MEMO_CASH,
            purpose = CASH,
            data = mapOf("player" to player.uuid.toString(), "casino" to handle),
        )
    }

    fun handleChange(payload: CounterChangePayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        val handle = casinoAt(player, payload.pos) ?: return
        if (payload.largest !in Denominations.ALL) return
        val value = Chips.takeAll(player.inventory, handle)
        if (value == 0L) {
            error(player, Component.translatable("economy.lnbeconomy.counter.no_chips", Handles.display(handle)))
            return
        }
        for (stack in Chips.make(handle, value, payload.largest)) {
            if (!player.inventory.add(stack)) player.drop(stack, false)
        }
        player.sendSystemMessage(Component.translatable("economy.lnbeconomy.counter.changed", Texts.amount(value, player)))
    }

    private fun finishBuy(server: MinecraftServer, entry: JournalEntry, outcome: Outcome) {
        val uuid = UUID.fromString(entry.data.getValue("player"))
        val handle = entry.data.getValue("casino")
        val player = server.playerList.getPlayer(uuid)
        when (outcome) {
            is Outcome.Delayed -> player?.sendSystemMessage(Component.translatable("economy.lnbeconomy.counter.delayed").withStyle(ChatFormatting.GRAY))
            is Outcome.Refused -> player?.let { error(it, Texts.problem(outcome.result)) }
            is Outcome.Done -> {
                PlayerAccounts.applyTransfer(server, outcome.result)
                ChipLedger.get(server).add(handle, outcome.result.amount)
                ChipMailbox.deliver(server, uuid, handle, outcome.result.amount)
                player?.sendSystemMessage(
                    Component.translatable("economy.lnbeconomy.counter.bought", Texts.amount(outcome.result.amount, player), Handles.display(handle))
                        .withStyle(ChatFormatting.GREEN),
                )
            }
        }
    }

    private fun finishCash(server: MinecraftServer, entry: JournalEntry, outcome: Outcome) {
        val uuid = UUID.fromString(entry.data.getValue("player"))
        val handle = entry.data.getValue("casino")
        val player = server.playerList.getPlayer(uuid)
        when (outcome) {
            is Outcome.Delayed -> player?.sendSystemMessage(Component.translatable("economy.lnbeconomy.counter.delayed").withStyle(ChatFormatting.GRAY))
            is Outcome.Refused -> {
                ChipLedger.get(server).add(handle, entry.body.amount)
                ChipMailbox.deliver(server, uuid, handle, entry.body.amount)
                player?.let {
                    val problem = (outcome.result as? ApiResult.Http)?.problem
                    val reason = if (problem == Problems.INSUFFICIENT_FUNDS) Component.translatable("economy.lnbeconomy.counter.casino_short", Handles.display(handle))
                    else Texts.problem(outcome.result)
                    error(it, Component.translatable("economy.lnbeconomy.counter.cash_refused", reason))
                }
            }
            is Outcome.Done -> {
                PlayerAccounts.applyTransfer(server, outcome.result)
                player?.sendSystemMessage(
                    Component.translatable("economy.lnbeconomy.counter.cashed", Texts.amount(outcome.result.amount, player)).withStyle(ChatFormatting.GREEN),
                )
            }
        }
    }

    /** The casino of the counter at [pos], if the player can reach it. */
    private fun casinoAt(player: ServerPlayer, pos: BlockPos): String? {
        val level = player.serverLevel()
        if (!level.isLoaded(pos) || player.distanceToSqr(pos.center) > REACH * REACH) return null
        if (!level.getBlockState(pos).`is`(ModContent.COUNTER.get())) return null
        return CasinoLinks.get(player.server).get(level.dimension(), pos)?.handle
    }

    private fun hasEconomy(player: ServerPlayer): Boolean {
        PlayerAccounts.noEconomyMessage(player.uuid)?.let {
            error(player, it)
            return false
        }
        if (PlayerAccounts.get(player.uuid)?.status != EconomyStatus.READY) {
            error(player, Component.translatable("economy.lnbeconomy.problem.unavailable"))
            return false
        }
        return true
    }

    private fun casinoOpen(player: ServerPlayer, handle: String): Boolean {
        val message = when (Casinos.status(player.server, handle)) {
            CasinoBook.Status.Frozen -> Component.translatable("economy.lnbeconomy.casino.frozen", Handles.display(handle))
            CasinoBook.Status.Missing -> Component.translatable("economy.lnbeconomy.casino.missing", Handles.display(handle))
            else -> return true
        }
        error(player, message)
        return false
    }

    private fun error(player: ServerPlayer, message: Component) {
        player.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED))
    }
}
