package fr.bde_teknantes.lnb.economy.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import fr.bde_teknantes.lnb.economy.account.KnownHandles
import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.account.PlayerAccounts
import fr.bde_teknantes.lnb.economy.api.EconomyApi
import fr.bde_teknantes.lnb.economy.api.Handles
import fr.bde_teknantes.lnb.economy.casino.CasinoBook
import fr.bde_teknantes.lnb.economy.casino.CasinoLinks
import fr.bde_teknantes.lnb.economy.casino.Casinos
import fr.bde_teknantes.lnb.economy.chip.ChipLedger
import fr.bde_teknantes.lnb.economy.ledger.TransferJournal
import fr.bde_teknantes.lnb.economy.money.Texts
import fr.bde_teknantes.lnb.economy.network.EconomyStatus
import fr.bde_teknantes.lnb.warden.server.api.ApiHealth
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.event.RegisterCommandsEvent
import java.util.concurrent.CompletableFuture

/** `/balance`, `/pay`, `/payfrom`, `/payconfirm` for everyone, and `/economy` for operators. */
object EconomyCommands {
    private const val OPERATOR = 2

    fun register(event: RegisterCommandsEvent) {
        val dispatcher = event.dispatcher
        registerBalance(dispatcher, "balance")
        registerBalance(dispatcher, "bal")

        dispatcher.register(
            Commands.literal("pay").then(
                Commands.argument("payment", StringArgumentType.greedyString())
                    .suggests { context, builder -> suggestPayment(context, builder, 0) }
                    .executes { context -> pay(context, null, StringArgumentType.getString(context, "payment")) },
            ),
        )
        dispatcher.register(
            Commands.literal("payfrom").then(
                Commands.argument("payment", StringArgumentType.greedyString())
                    .suggests { context, builder -> suggestPayment(context, builder, 1) }
                    .executes { context ->
                        val text = StringArgumentType.getString(context, "payment").trim()
                        val from = text.substringBefore(' ')
                        if (!Handles.isHandle(from) || !text.contains(' ')) {
                            context.source.sendFailure(Component.translatable("economy.lnbeconomy.payfrom.usage"))
                            0
                        } else {
                            pay(context, from, text.substringAfter(' '))
                        }
                    },
            ),
        )
        dispatcher.register(
            Commands.literal("payconfirm").then(
                Commands.argument("token", StringArgumentType.word()).executes { context ->
                    Payments.confirm(context.source.playerOrException, StringArgumentType.getString(context, "token"))
                    1
                },
            ),
        )

        val economy = Commands.literal("economy").requires { it.hasPermission(OPERATOR) }
            .then(Commands.literal("status").executes(::status))
            .then(Commands.literal("pending").executes(::pending))
            .then(
                Commands.literal("retry").then(
                    Commands.argument("key", StringArgumentType.word()).executes { context ->
                        val key = StringArgumentType.getString(context, "key")
                        if (TransferJournal.retryNow(key)) {
                            context.source.sendSuccess({ Component.translatable("commands.lnbeconomy.retry", key) }, true)
                            1
                        } else {
                            context.source.sendFailure(Component.translatable("commands.lnbeconomy.retry.unknown", key))
                            0
                        }
                    },
                ),
            )
            .then(
                Commands.literal("casino").then(
                    Commands.argument("casino", StringArgumentType.greedyString())
                        .suggests { _, builder ->
                            KnownHandles.extra().map(Handles::display).filter { it.startsWith(builder.remaining) }.forEach(builder::suggest)
                            builder.buildFuture()
                        }
                        .executes(::casino),
                ),
            )
        dispatcher.register(economy)
    }

    private fun registerBalance(dispatcher: CommandDispatcher<CommandSourceStack>, name: String) {
        dispatcher.register(Commands.literal(name).executes { context ->
            val player = context.source.playerOrException
            PlayerAccounts.refresh(player.server, player.uuid, force = false).thenAccept { showBalance(player) }
            1
        })
    }

    private fun showBalance(player: ServerPlayer) {
        PlayerAccounts.noEconomyMessage(player.uuid)?.let {
            player.sendSystemMessage(it.copy().withStyle(ChatFormatting.RED))
            return
        }
        val accounts = PlayerAccounts.accounts(player.uuid)
        if (accounts == null) {
            player.sendSystemMessage(Component.translatable("economy.lnbeconomy.problem.unavailable").withStyle(ChatFormatting.RED))
            return
        }
        player.sendSystemMessage(Component.translatable("economy.lnbeconomy.balance.title").withStyle(ChatFormatting.GOLD))
        for (account in accounts.accounts) {
            val name = if (account.isPersonal) Component.translatable("economy.lnbeconomy.balance.personal")
            else Component.literal("${account.name} (${Handles.display(account.handle!!)})")
            val line = Component.translatable("economy.lnbeconomy.balance.line", name, Texts.amount(account.balance, player))
            if (account.id == accounts.defaultAccountId) line.append(Component.translatable("economy.lnbeconomy.balance.default").withStyle(ChatFormatting.YELLOW))
            if (account.frozen) line.append(Component.translatable("economy.lnbeconomy.balance.frozen").withStyle(ChatFormatting.AQUA))
            player.sendSystemMessage(line)
        }
    }

    private fun pay(context: CommandContext<CommandSourceStack>, from: String?, text: String): Int {
        val player = context.source.playerOrException
        when (val parsed = PayArguments.parse(text)) {
            is PayArguments.Result.Error -> {
                context.source.sendFailure(Component.translatable(parsed.key))
                return 0
            }
            is PayArguments.Result.Ok -> Payments.pay(player, from, parsed.value)
        }
        return 1
    }

    /**
     * Completes the token being typed. [targetIndex] is where the target sits: 0 for `/pay`, 1 for `/payfrom`
     * (whose first token is the paying account).
     */
    private fun suggestPayment(context: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder, targetIndex: Int): CompletableFuture<Suggestions> {
        val player = context.source.player ?: return builder.buildFuture()
        val typed = builder.remaining
        val index = typed.count { it == ' ' }
        val start = typed.lastIndexOf(' ') + 1
        val current = typed.substring(start)
        val tokenBuilder = builder.createOffset(builder.start + start)
        val own = PlayerAccounts.accounts(player.uuid)?.accounts.orEmpty()

        val options: List<String> = when (index) {
            targetIndex - 1 -> own.filter { it.handle != null && it.canSpend }.map { Handles.display(it.handle!!) }
            targetIndex -> {
                val names = player.server.playerList.players.map { it.gameProfile.name }.filter { it != player.gameProfile.name }
                val handles = (own.mapNotNull { it.handle } + KnownHandles.extra()).map(Handles::display)
                names + handles.distinct()
            }
            else -> emptyList()
        }
        options.filter { it.startsWith(current, ignoreCase = true) }.sorted().forEach(tokenBuilder::suggest)
        return tokenBuilder.buildFuture()
    }

    private fun status(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val configured = EconomyApi.connection() != null
        source.sendSuccess({
            Component.translatable(
                "commands.lnbeconomy.status.api",
                Component.translatable(if (configured) "commands.lnbeconomy.status.configured" else "commands.lnbeconomy.status.missing"),
                ApiHealth.lastSuccess?.toString() ?: "-",
                ApiHealth.lastError?.let { "${it.at} (${it.message})" } ?: "-",
            )
        }, false)
        val players = source.server.playerList.players
        val ready = players.count { PlayerAccounts.get(it.uuid)?.status == EconomyStatus.READY }
        source.sendSuccess({ Component.translatable("commands.lnbeconomy.status.players", ready, players.size) }, false)
        source.sendSuccess({ Component.translatable("commands.lnbeconomy.status.pending", TransferJournal.pending.size) }, false)
        return 1
    }

    /**
     * `/economy casino #handle`: the casino's balance, free balance, chips in circulation and links.
     * `/economy casino #handle outstanding <n>`: sets its chips in circulation, after chips were lost unseen.
     */
    private fun casino(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val parts = StringArgumentType.getString(context, "casino").trim().split(Regex("\\s+"))
        if (!Handles.isHandle(parts[0])) {
            source.sendFailure(Component.translatable("economy.lnbeconomy.pay.bad_handle"))
            return 0
        }
        val handle = Handles.bare(parts[0])
        val ledger = ChipLedger.get(source.server)
        if (parts.size == 3 && parts[1] == "outstanding") {
            val value = parts[2].toLongOrNull()?.takeIf { it >= 0 } ?: run {
                source.sendFailure(Component.translatable("economy.lnbeconomy.pay.bad_amount"))
                return 0
            }
            val before = ledger.outstanding(handle)
            ledger.set(handle, value)
            LnbEconomy.LOGGER.info("{} set the chips in circulation of #{} from {} to {}", source.textName, handle, before, value)
            source.sendSuccess({ Component.translatable("commands.lnbeconomy.casino.outstanding.set", Handles.display(handle), before, value) }, true)
            return 1
        }
        if (parts.size != 1) {
            source.sendFailure(Component.translatable("commands.lnbeconomy.casino.usage"))
            return 0
        }
        Casinos.refresh(source.server, handle).thenAccept { status ->
            val account = Casinos.book.account(handle)
            val free = when (status) {
                is CasinoBook.Status.Open -> status.free.toString()
                else -> status.javaClass.simpleName
            }
            source.sendSuccess({
                Component.translatable(
                    "commands.lnbeconomy.casino.info",
                    Handles.display(handle),
                    account?.balance?.toString() ?: "?",
                    ledger.outstanding(handle),
                    Casinos.book.reserved(handle),
                    free,
                    CasinoLinks.get(source.server).linkedTo(handle).size,
                )
            }, false)
        }
        return 1
    }

    private fun pending(context: CommandContext<CommandSourceStack>): Int {
        val entries = TransferJournal.pending
        if (entries.isEmpty()) {
            context.source.sendSuccess({ Component.translatable("commands.lnbeconomy.pending.none") }, false)
            return 1
        }
        for (entry in entries) {
            val transfer = entry.transfer
            val retry = Component.translatable("commands.lnbeconomy.pending.retry").withStyle { style ->
                style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                    .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/economy retry ${entry.key}"))
                    .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(entry.key)))
            }
            val line = Component.translatable(
                "commands.lnbeconomy.pending.line",
                entry.purpose,
                entry.amount?.toString() ?: "-",
                transfer?.from?.let { it.account?.let(Handles::display) ?: it.player } ?: entry.route,
                transfer?.to?.let { it.account?.let(Handles::display) ?: it.player } ?: "-",
                entry.attempts,
                entry.lastError ?: "-",
            ).append(" ").append(retry)
            context.source.sendSuccess({ line }, false)
        }
        return entries.size
    }
}
