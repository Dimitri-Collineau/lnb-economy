package fr.bde_teknantes.lnb.economy.trade

import com.mojang.brigadier.context.CommandContext
import fr.bde_teknantes.lnb.economy.EconomyConfig
import fr.bde_teknantes.lnb.economy.account.PlayerAccounts
import fr.bde_teknantes.lnb.economy.account.RealPlayers
import fr.bde_teknantes.lnb.economy.api.TradeBody
import fr.bde_teknantes.lnb.economy.api.TradeSide
import fr.bde_teknantes.lnb.economy.api.TransferBody
import fr.bde_teknantes.lnb.economy.item.Escrow
import fr.bde_teknantes.lnb.economy.ledger.JournalEntry
import fr.bde_teknantes.lnb.economy.ledger.Outcome
import fr.bde_teknantes.lnb.economy.ledger.TransferJournal
import fr.bde_teknantes.lnb.economy.mail.ItemMailbox
import fr.bde_teknantes.lnb.economy.money.Texts
import fr.bde_teknantes.lnb.economy.network.TradeMoneyPayload
import fr.bde_teknantes.lnb.economy.network.TradeReadyPayload
import fr.bde_teknantes.lnb.economy.network.TradeRequestPayload
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/**
 * Trades between players. `/trade <player>` asks from any distance; the trade key asks the player in front of you.
 * Once both are ready and the countdown ends, both sides' items leave the inventories together and are held in the
 * journal entry while the site moves the money; each player then gets the other's items, or their own back.
 */
object Trades {
    private const val PURPOSE = "trade"
    private const val REQUEST_MS = 60_000L
    private const val KEY_REACH = 5.0
    private val MAIN_INVENTORY = 0 until 36

    private object Data {
        const val FIRST = "first"
        const val SECOND = "second"
        const val FIRST_ITEMS = "firstItems"
        const val SECOND_ITEMS = "secondItems"
        const val FIRST_NAME = "firstName"
        const val SECOND_NAME = "secondName"
    }

    /** Asked player -> who asked them, and until when. */
    private val requests = HashMap<UUID, MutableMap<UUID, Long>>()
    private val sessions = HashMap<UUID, TradeSession>()

    fun register() {
        TransferJournal.register(PURPOSE, ::finish)
    }

    fun registerCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("trade")
                .then(Commands.argument("player", EntityArgument.player()).executes { ask(it.source.playerOrException, EntityArgument.getPlayer(it, "player")) })
                .then(Commands.literal("accept").then(Commands.argument("player", EntityArgument.player()).executes { answer(it, accept = true) }))
                .then(Commands.literal("decline").then(Commands.argument("player", EntityArgument.player()).executes { answer(it, accept = false) })),
        )
    }

    private fun answer(context: CommandContext<CommandSourceStack>, accept: Boolean): Int {
        val player = context.source.playerOrException
        val asker = EntityArgument.getPlayer(context, "player")
        val asked = requests[player.uuid]?.remove(asker.uuid)
        if (asked == null || asked < System.currentTimeMillis()) {
            tell(player, Component.translatable("economy.lnbeconomy.trade.no_request", asker.gameProfile.name))
            return 0
        }
        if (!accept) {
            tell(asker, Component.translatable("economy.lnbeconomy.trade.declined", player.gameProfile.name))
            return 1
        }
        if (!canTrade(asker, player) || !canTrade(player, asker)) return 0
        val session = TradeSession(player.server, asker.uuid, player.uuid)
        sessions[asker.uuid] = session
        sessions[player.uuid] = session
        session.open()
        return 1
    }

    /** The trade key: asks the player the sender looks at, if they are close. */
    fun handleRequest(payload: TradeRequestPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        val target = player.serverLevel().getEntity(payload.entityId) as? ServerPlayer ?: return
        // A little more than the key's reach, for the movement between the client's look and the server.
        if (player.distanceTo(target) > KEY_REACH + 1) return
        ask(player, target)
    }

    private fun ask(player: ServerPlayer, target: ServerPlayer): Int {
        if (!canTrade(player, target)) return 0
        requests.getOrPut(target.uuid, ::HashMap)[player.uuid] = System.currentTimeMillis() + REQUEST_MS
        val name = player.gameProfile.name
        val accept = button("economy.lnbeconomy.trade.accept", "/trade accept $name", ChatFormatting.GREEN)
        val decline = button("economy.lnbeconomy.trade.decline", "/trade decline $name", ChatFormatting.RED)
        target.sendSystemMessage(Component.translatable("economy.lnbeconomy.trade.asked", name).append(" ").append(accept).append(" ").append(decline))
        tell(player, Component.translatable("economy.lnbeconomy.trade.sent", target.gameProfile.name).withStyle(ChatFormatting.GRAY))
        return 1
    }

    private fun canTrade(player: ServerPlayer, target: ServerPlayer): Boolean {
        val problem = when {
            !EconomyConfig.TRADES.get() -> "switched_off"
            !RealPlayers.isReal(player) || !RealPlayers.isReal(target) -> "not_a_player"
            player.uuid == target.uuid -> "yourself"
            sameLnbAccount(player.uuid, target.uuid) -> "same_person"
            player.uuid in sessions || target.uuid in sessions -> "busy"
            else -> null
        } ?: return true
        tell(player, Component.translatable("economy.lnbeconomy.trade.cant.$problem", target.gameProfile.name))
        return false
    }

    /** Two Minecraft accounts of the same LNB account, when both are known. The site refuses them too. */
    private fun sameLnbAccount(first: UUID, second: UUID): Boolean {
        val a = PlayerAccounts.accounts(first)?.userId ?: return false
        return a == PlayerAccounts.accounts(second)?.userId
    }

    fun handleMoney(payload: TradeMoneyPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        val session = sessions[player.uuid] ?: return
        if (payload.amount !in 0..TransferBody.MAX_AMOUNT) return
        if (payload.amount > 0) {
            // Null is the default account, as on the site.
            val accounts = PlayerAccounts.accounts(player.uuid)
            val id = payload.accountId ?: accounts?.defaultAccountId
            val account = accounts?.accounts?.firstOrNull { it.id == id }
            if (account == null || !account.canSpend) {
                tell(player, Component.translatable("economy.lnbeconomy.problem.notAllowed"))
                return
            }
        }
        session.setMoney(player.uuid, payload.amount, payload.accountId)
    }

    fun handleReady(payload: TradeReadyPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        sessions[player.uuid]?.setReady(player.uuid, payload.ready)
    }

    fun onServerTick(event: ServerTickEvent.Post) {
        for (session in sessions.values.toSet()) {
            if (session.tick()) execute(session)
        }
    }

    fun cancel(session: TradeSession, by: UUID?) {
        if (!session.isOpen) return
        forget(session)
        session.close()
        val name = by?.let { session.server.playerList.getPlayer(it)?.gameProfile?.name }.orEmpty()
        for (uuid in listOf(session.first, session.second)) {
            if (uuid == by) continue
            session.server.playerList.getPlayer(uuid)?.let { tell(it, Component.translatable("economy.lnbeconomy.trade.cancelled", name)) }
        }
    }

    private fun forget(session: TradeSession) {
        sessions.remove(session.first, session)
        sessions.remove(session.second, session)
    }

    /**
     * Runs the trade: takes both offers out of the inventories together, then moves the money. When the money cancels
     * out, the items swap right away; otherwise they wait in the journal entry for the site's answer.
     */
    fun execute(session: TradeSession) {
        val server = session.server
        val first = server.playerList.getPlayer(session.first)
        val second = server.playerList.getPlayer(session.second)
        forget(session)
        session.close()
        if (first == null || second == null) return

        val firstTotals = TradeTerms.totals(session.firstSide.offer)
        val secondTotals = TradeTerms.totals(session.secondSide.offer)
        val missing = when {
            !TradeTerms.holds(first.inventory, firstTotals, MAIN_INVENTORY) -> first
            !TradeTerms.holds(second.inventory, secondTotals, MAIN_INVENTORY) -> second
            else -> null
        }
        if (missing != null) {
            for (player in listOf(first, second)) tell(player, Component.translatable("economy.lnbeconomy.trade.missing_items", missing.gameProfile.name))
            return
        }
        val firstItems = TradeTerms.take(first.inventory, firstTotals, MAIN_INVENTORY).orEmpty()
        val secondItems = TradeTerms.take(second.inventory, secondTotals, MAIN_INVENTORY).orEmpty()

        val settlement = TradeTerms.settle(session.firstSide.amount, session.secondSide.amount)
        if (settlement == null) {
            ItemMailbox.deliver(server, second.uuid, firstItems)
            ItemMailbox.deliver(server, first.uuid, secondItems)
            for ((player, other) in listOf(first to second, second to first)) {
                tell(player, Component.translatable("economy.lnbeconomy.trade.done", other.gameProfile.name).withStyle(ChatFormatting.GREEN))
            }
            return
        }
        val registries = server.registryAccess()
        val data = mapOf(
            Data.FIRST to first.uuid.toString(),
            Data.SECOND to second.uuid.toString(),
            Data.FIRST_ITEMS to Escrow.hold(firstItems, registries),
            Data.SECOND_ITEMS to Escrow.hold(secondItems, registries),
            Data.FIRST_NAME to first.gameProfile.name,
            Data.SECOND_NAME to second.gameProfile.name,
        )
        val (payer, payee) = if (settlement.payerIsFirst) session.firstSide to session.secondSide else session.secondSide to session.firstSide
        TransferJournal.submit("/trades", PURPOSE, data) { key ->
            TradeBody(
                idempotencyKey = key,
                from = TradeSide(payer.player.toString(), payer.accountId),
                to = TradeSide(payee.player.toString(), payee.accountId),
                amount = settlement.amount,
            )
        }
    }

    private fun finish(server: MinecraftServer, entry: JournalEntry, outcome: Outcome) {
        val first = UUID.fromString(entry.data.getValue(Data.FIRST))
        val second = UUID.fromString(entry.data.getValue(Data.SECOND))
        val names = mapOf(first to entry.data[Data.FIRST_NAME].orEmpty(), second to entry.data[Data.SECOND_NAME].orEmpty())
        val players = listOf(first, second)
        when (outcome) {
            is Outcome.Delayed -> players.forEach { uuid ->
                server.playerList.getPlayer(uuid)?.let { tell(it, Component.translatable("economy.lnbeconomy.trade.delayed").withStyle(ChatFormatting.GRAY)) }
            }
            is Outcome.Refused -> {
                Escrow.release(server, entry, Data.FIRST_ITEMS, first)
                Escrow.release(server, entry, Data.SECOND_ITEMS, second)
                players.forEach { uuid ->
                    server.playerList.getPlayer(uuid)?.let {
                        tell(it, Component.translatable("economy.lnbeconomy.trade.refused", Texts.problem(outcome.result)).withStyle(ChatFormatting.RED))
                    }
                }
            }
            is Outcome.Done -> {
                PlayerAccounts.applyTransfer(server, outcome.result)
                Escrow.release(server, entry, Data.FIRST_ITEMS, second)
                Escrow.release(server, entry, Data.SECOND_ITEMS, first)
                for (uuid in players) {
                    val other = if (uuid == first) second else first
                    server.playerList.getPlayer(uuid)?.let {
                        tell(it, Component.translatable("economy.lnbeconomy.trade.done", names[other].orEmpty()).withStyle(ChatFormatting.GREEN))
                    }
                }
            }
        }
    }

    fun onLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        requests.remove(event.entity.uuid)
        sessions[event.entity.uuid]?.let { cancel(it, event.entity.uuid) }
    }

    fun onServerStopped(event: ServerStoppedEvent) {
        requests.clear()
        sessions.clear()
    }

    private fun button(key: String, command: String, color: ChatFormatting): Component =
        Component.translatable(key).withStyle { style ->
            style.withColor(color).withBold(true)
                .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command)))
        }

    private fun tell(player: ServerPlayer, message: Component) = player.sendSystemMessage(message)
}
