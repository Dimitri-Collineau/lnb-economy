package fr.bde_teknantes.lnb.economy.command

import fr.bde_teknantes.lnb.economy.EconomyConfig
import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.account.PlayerAccounts
import fr.bde_teknantes.lnb.economy.account.SpendLimits
import fr.bde_teknantes.lnb.economy.api.Handles
import fr.bde_teknantes.lnb.economy.api.Party
import fr.bde_teknantes.lnb.economy.api.Problems
import fr.bde_teknantes.lnb.economy.api.TransferBody
import fr.bde_teknantes.lnb.economy.ledger.JournalEntry
import fr.bde_teknantes.lnb.economy.ledger.Outcome
import fr.bde_teknantes.lnb.economy.ledger.TransferJournal
import fr.bde_teknantes.lnb.economy.money.Texts
import fr.bde_teknantes.lnb.economy.network.EconomyStatus
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/** `/pay` and `/payfrom`: from a check on the server, through the journal, to the messages on both sides. */
object Payments {
    const val PURPOSE = "pay"

    private const val CONFIRM_MS = 30_000L

    private data class Request(val from: String?, val target: PayArguments.Target, val targetUuid: UUID?, val amount: Long, val memo: String?)

    private data class Confirmation(val token: String, val request: Request, val expiresAt: Long)

    private val confirmations = HashMap<UUID, Confirmation>()

    fun register() {
        TransferJournal.register(PURPOSE, ::finish)
    }

    /** [from] is a handle for `/payfrom`, null for the default account. */
    fun pay(player: ServerPlayer, from: String?, parsed: PayArguments.Parsed) {
        val targetUuid = when (val target = parsed.target) {
            is PayArguments.Target.Account -> null
            is PayArguments.Target.Player -> uuidOf(player.server, target.name) ?: run {
                error(player, Component.translatable("economy.lnbeconomy.pay.unknown_player", target.name))
                return
            }
        }
        val request = Request(from?.let(Handles::bare), parsed.target, targetUuid, parsed.amount, parsed.memo)
        if (!mayPay(player, request)) return

        val threshold = EconomyConfig.PAY_CONFIRM_ABOVE.get()
        if (threshold in 1 until request.amount) {
            val token = UUID.randomUUID().toString().take(8)
            confirmations[player.uuid] = Confirmation(token, request, System.currentTimeMillis() + CONFIRM_MS)
            val button = Component.translatable("economy.lnbeconomy.pay.confirm.button").withStyle { style ->
                style.withColor(ChatFormatting.GREEN).withUnderlined(true)
                    .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/payconfirm $token"))
                    .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("economy.lnbeconomy.pay.confirm.hover")))
            }
            player.sendSystemMessage(
                Component.translatable("economy.lnbeconomy.pay.confirm", Texts.amount(request.amount, player), targetName(request.target), button),
            )
            return
        }
        send(player, request)
    }

    fun confirm(player: ServerPlayer, token: String) {
        val pending = confirmations.remove(player.uuid)
        if (pending == null || pending.token != token || pending.expiresAt < System.currentTimeMillis()) {
            error(player, Component.translatable("economy.lnbeconomy.pay.confirm.expired"))
            return
        }
        if (mayPay(player, pending.request)) send(player, pending.request)
    }

    /** Local checks, so the obvious refusals don't need the site. The site checks everything again. */
    private fun mayPay(player: ServerPlayer, request: Request): Boolean {
        val entry = PlayerAccounts.get(player.uuid)
        PlayerAccounts.noEconomyMessage(player.uuid)?.let {
            error(player, it)
            return false
        }
        val accounts = entry?.player
        if (entry?.status != EconomyStatus.READY || accounts == null) {
            error(player, Component.translatable("economy.lnbeconomy.problem.unavailable"))
            PlayerAccounts.refresh(player.server, player.uuid, force = false)
            return false
        }
        val paying = if (request.from == null) accounts.defaultAccount else accounts.accounts.firstOrNull { it.handle == request.from }
        if (request.from != null && paying == null) {
            error(player, Component.translatable("economy.lnbeconomy.problem.notAllowed"))
            return false
        }
        if (paying != null && !paying.canSpend && request.from != null) {
            error(player, Component.translatable(if (paying.frozen) "economy.lnbeconomy.problem.frozen" else "economy.lnbeconomy.problem.notAllowed"))
            return false
        }
        val handle = paying?.handle
        if (handle != null) {
            val available = SpendLimits.available(handle)
            if (available != null && request.amount > available) {
                error(player, Component.translatable("economy.lnbeconomy.pay.casino_reserved", Handles.display(handle), Texts.amount(maxOf(0, available), player)))
                return false
            }
        }
        return true
    }

    private fun send(player: ServerPlayer, request: Request) {
        val to = when (val target = request.target) {
            is PayArguments.Target.Account -> Party(account = target.handle)
            is PayArguments.Target.Player -> Party(player = request.targetUuid.toString())
        }
        val data = buildMap {
            put("payer", player.uuid.toString())
            put("payerName", player.gameProfile.name)
            put("target", targetText(request.target))
            request.memo?.let { put("memo", it) }
        }
        TransferJournal.submit(
            from = Party(player = player.uuid.toString(), account = request.from),
            to = to,
            amount = request.amount,
            kind = TransferBody.KIND_PAYMENT,
            memo = request.memo,
            purpose = PURPOSE,
            data = data,
        )
    }

    private fun finish(server: MinecraftServer, entry: JournalEntry, outcome: Outcome) {
        val payer = entry.data["payer"]?.let { server.playerList.getPlayer(UUID.fromString(it)) }
        val target = entry.data["target"].orEmpty()
        when (outcome) {
            is Outcome.Delayed -> payer?.sendSystemMessage(Component.translatable("economy.lnbeconomy.pay.delayed").withStyle(ChatFormatting.GRAY))
            is Outcome.Refused -> payer?.let { error(it, refusal(outcome.result, target)) }
            is Outcome.Done -> {
                val result = outcome.result
                PlayerAccounts.applyTransfer(server, result)
                if (result.replayed) LnbEconomy.LOGGER.info("Payment {} had already been made", entry.key)
                payer?.sendSystemMessage(
                    Component.translatable(
                        "economy.lnbeconomy.pay.sent",
                        Texts.amount(result.amount, payer),
                        Component.literal(target),
                        Texts.account(result.from.handle),
                    ).withStyle(ChatFormatting.GREEN),
                )
                notifyReceivers(server, entry, result.to.accountId, result.to.handle, result.amount)
            }
        }
    }

    private fun notifyReceivers(server: MinecraftServer, entry: JournalEntry, accountId: String, handle: String?, amount: Long) {
        val payerUuid = entry.data["payer"]
        val payerName = entry.data["payerName"].orEmpty()
        val memo = entry.data["memo"]
        for (receiver in PlayerAccounts.holdersOf(server, accountId)) {
            if (receiver.uuid.toString() == payerUuid) continue
            val key = when {
                handle == null && memo == null -> "economy.lnbeconomy.pay.received"
                handle == null -> "economy.lnbeconomy.pay.received.memo"
                memo == null -> "economy.lnbeconomy.pay.received.account"
                else -> "economy.lnbeconomy.pay.received.account.memo"
            }
            val args = buildList<Any> {
                add(payerName)
                add(Texts.amount(amount, receiver))
                handle?.let { add(Handles.display(it)) }
                memo?.let { add(it) }
            }
            receiver.sendSystemMessage(Component.translatable(key, *args.toTypedArray()).withStyle(ChatFormatting.GREEN))
        }
    }

    /** The receiving side's problems talk about the receiver. */
    private fun refusal(result: ApiResult<*>, target: String): Component {
        val problem = (result as? ApiResult.Http)?.problem
        return when {
            Problems.isNoEconomy(problem) -> Component.translatable("economy.lnbeconomy.pay.target_no_economy", target)
            problem == Problems.NOT_FOUND -> Component.translatable("economy.lnbeconomy.pay.target_not_found", target)
            problem == Problems.IDEMPOTENCY_MISMATCH -> {
                LnbEconomy.LOGGER.error("A payment was refused as idempotencyMismatch: two different payments used the same key")
                Component.translatable("economy.lnbeconomy.problem.other", Problems.code(result))
            }
            else -> Texts.problem(result)
        }
    }

    /** Online players first, then the profile cache, then the offline-mode UUID (the LNB server runs offline). */
    private fun uuidOf(server: MinecraftServer, name: String): UUID? {
        server.playerList.getPlayerByName(name)?.let { return it.uuid }
        server.profileCache?.get(name)?.orElse(null)?.let { if (it.name == name) return it.id }
        if (server.usesAuthentication()) return null
        return UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8))
    }

    private fun targetText(target: PayArguments.Target) = when (target) {
        is PayArguments.Target.Account -> Handles.display(target.handle)
        is PayArguments.Target.Player -> target.name
    }

    private fun targetName(target: PayArguments.Target): Component = Component.literal(targetText(target))

    private fun error(player: ServerPlayer, message: Component) {
        player.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED))
    }
}
