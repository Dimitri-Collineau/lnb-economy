package fr.bde_teknantes.lnb.economy.account

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import fr.bde_teknantes.lnb.economy.api.AccountCreateBody
import fr.bde_teknantes.lnb.economy.api.AccountPatchBody
import fr.bde_teknantes.lnb.economy.api.AccountTypes
import fr.bde_teknantes.lnb.economy.api.AccountView
import fr.bde_teknantes.lnb.economy.api.EconomyApi
import fr.bde_teknantes.lnb.economy.api.Handles
import fr.bde_teknantes.lnb.economy.api.InviteBody
import fr.bde_teknantes.lnb.economy.api.Problems
import fr.bde_teknantes.lnb.economy.casino.Casinos
import fr.bde_teknantes.lnb.economy.network.AccountCreatePayload
import fr.bde_teknantes.lnb.economy.network.AccountEditPayload
import fr.bde_teknantes.lnb.economy.network.AccountFeedbackPayload
import fr.bde_teknantes.lnb.economy.network.AccountInvitePayload
import fr.bde_teknantes.lnb.economy.network.AccountMembersPayload
import fr.bde_teknantes.lnb.economy.network.AccountMembersRequestPayload
import fr.bde_teknantes.lnb.economy.network.EconomyStatus
import fr.bde_teknantes.lnb.economy.network.InvitationAnswerPayload
import fr.bde_teknantes.lnb.economy.network.InvitationCancelPayload
import fr.bde_teknantes.lnb.economy.network.MemberEntry
import fr.bde_teknantes.lnb.economy.network.PendingEntry
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Shared accounts from the game: opening one, renaming it, changing its type, inviting players and answering
 * invitations, with `/account` (`/compte`) or from "My accounts". Every rule is the site's: this only asks, then
 * tells the player what the site answered.
 */
object SharedAccounts {
    private val ID = Regex("^[0-9a-fA-F-]{8,64}$")

    /** Where the answer goes: the chat for a command, the open screen for a request made from it. */
    private class Reply(val player: ServerPlayer, val screen: Boolean) {
        fun ok(key: String, vararg args: String) {
            player.sendSystemMessage(Component.translatable(key, *args).withStyle(ChatFormatting.GREEN))
            if (screen) PacketDistributor.sendToPlayer(player, AccountFeedbackPayload(true, key, args.toList()))
        }

        fun error(key: String, vararg args: String) {
            if (screen) {
                PacketDistributor.sendToPlayer(player, AccountFeedbackPayload(false, key, args.toList()))
            } else {
                player.sendSystemMessage(Component.translatable(key, *args).withStyle(ChatFormatting.RED))
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1f, 0.5f)
            }
        }

        fun problem(result: ApiResult<*>) = error(Problems.messageKey(result), Problems.code(result))

        /** Runs [then] on the server thread with the site's answer, or tells the player what went wrong. */
        fun <T> handle(call: CompletableFuture<ApiResult<T>>, then: (T) -> Unit) {
            val server = player.server
            call.thenAccept { result ->
                server.execute {
                    when (result) {
                        is ApiResult.Ok -> then(result.body)
                        else -> problem(result)
                    }
                }
            }
        }
    }

    private fun hasEconomy(reply: Reply): Boolean {
        val entry = PlayerAccounts.get(reply.player.uuid)
        if (entry?.player != null) return true
        reply.error(
            when (entry?.status) {
                EconomyStatus.NOT_LINKED -> "economy.lnbeconomy.problem.notLinked"
                EconomyStatus.NOT_ELIGIBLE -> "economy.lnbeconomy.problem.notEligible"
                else -> "economy.lnbeconomy.problem.unavailable"
            },
        )
        return false
    }

    // What players do

    private fun create(reply: Reply, rawHandle: String, rawName: String, type: String) {
        val handle = Handles.bare(rawHandle)
        val name = rawName.trim()
        (AccountRules.handleProblem(handle) ?: AccountRules.nameProblem(name) ?: AccountRules.typeProblem(type))?.let { return reply.error(it) }
        if (!hasEconomy(reply)) return
        val player = reply.player
        reply.handle(EconomyApi.createAccount(AccountCreateBody(player.uuid.toString(), handle, name, type))) { account ->
            PlayerAccounts.refresh(player.server, player.uuid)
            reply.ok("economy.lnbeconomy.account.created", account.name ?: name, Handles.display(handle))
        }
    }

    private fun edit(reply: Reply, rawHandle: String, rawName: String?, type: String?) {
        val handle = Handles.bare(rawHandle)
        val name = rawName?.trim()
        if (name == null && type == null) return
        (AccountRules.handleProblem(handle) ?: name?.let(AccountRules::nameProblem) ?: type?.let(AccountRules::typeProblem))?.let { return reply.error(it) }
        if (!hasEconomy(reply)) return
        val player = reply.player
        val server = player.server
        reply.handle(EconomyApi.patchAccount(handle, AccountPatchBody(player.uuid.toString(), name, type))) { account ->
            // Everyone who holds the account sees its new name, and so do its machines and counters.
            (PlayerAccounts.holdersOf(server, account.id).map { it.uuid } + player.uuid).distinct().forEach { PlayerAccounts.refresh(server, it) }
            Casinos.refresh(server, handle)
            if (name != null) reply.ok("economy.lnbeconomy.account.renamed", Handles.display(handle), account.name ?: name)
            if (type != null) reply.ok("economy.lnbeconomy.account.type_set", account.name ?: Handles.display(handle), type)
        }
    }

    private fun members(reply: Reply, rawHandle: String) {
        val handle = Handles.bare(rawHandle)
        AccountRules.handleProblem(handle)?.let { return reply.error(it) }
        val player = reply.player
        reply.handle(EconomyApi.members(handle, player.uuid)) { answer ->
            if (reply.screen) {
                PacketDistributor.sendToPlayer(
                    player,
                    AccountMembersPayload(
                        handle,
                        answer.members.map { MemberEntry(it.username ?: "?", it.role) },
                        answer.invitations.map { PendingEntry(it.id, it.username) },
                    ),
                )
                return@handle
            }
            player.sendSystemMessage(
                Component.translatable("economy.lnbeconomy.account.members.title", answer.account.name ?: Handles.display(handle)).withStyle(ChatFormatting.GOLD),
            )
            for (member in answer.members) {
                player.sendSystemMessage(
                    Component.translatable("economy.lnbeconomy.account.members.line", member.username ?: "?", Component.translatable("gui.lnbeconomy.accounts.role.${member.role}")),
                )
            }
            for (invitation in answer.invitations) {
                player.sendSystemMessage(Component.translatable("economy.lnbeconomy.account.members.invited", invitation.username).withStyle(ChatFormatting.GRAY))
            }
        }
    }

    private fun invite(reply: Reply, rawHandle: String, username: String) {
        val handle = Handles.bare(rawHandle)
        AccountRules.handleProblem(handle)?.let { return reply.error(it) }
        if (!AccountRules.isUsername(username)) return reply.error("economy.lnbeconomy.pay.bad_player")
        if (!hasEconomy(reply)) return
        val player = reply.player
        val server = player.server
        reply.handle(EconomyApi.invite(handle, InviteBody(player.uuid.toString(), username))) { invitation ->
            reply.ok("economy.lnbeconomy.account.invited", invitation.username, invitation.account.name)
            if (reply.screen) members(reply, handle)
            // Tell the invited player on every Minecraft account of theirs that is on.
            for (uuid in invitation.inviteeUuids.orEmpty().mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }) {
                val invited = server.playerList.getPlayer(uuid) ?: continue
                PlayerAccounts.refresh(server, uuid)
                invited.sendSystemMessage(invitationMessage(invitation.id, invitation.account.name, invitation.account.handle, invitation.invitedBy ?: player.gameProfile.name))
                invited.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.2f)
            }
        }
    }

    private fun cancel(reply: Reply, rawHandle: String, invitationId: String) {
        val handle = Handles.bare(rawHandle)
        if (AccountRules.handleProblem(handle) != null || !ID.matches(invitationId)) return
        reply.handle(EconomyApi.cancelInvitation(handle, invitationId, reply.player.uuid)) {
            reply.ok("economy.lnbeconomy.account.invitation_cancelled")
            if (reply.screen) members(reply, handle)
        }
    }

    private fun answer(reply: Reply, invitationId: String, accept: Boolean) {
        if (!ID.matches(invitationId)) return reply.error("economy.lnbeconomy.problem.invitationGone")
        val player = reply.player
        val server = player.server
        if (accept) {
            reply.handle(EconomyApi.acceptInvitation(invitationId, player.uuid)) { account ->
                PlayerAccounts.refresh(server, player.uuid)
                reply.ok("economy.lnbeconomy.account.joined", account.name ?: Handles.display(account.handle.orEmpty()))
            }
        } else {
            reply.handle(EconomyApi.declineInvitation(invitationId, player.uuid)) {
                PlayerAccounts.refresh(server, player.uuid)
                reply.ok("economy.lnbeconomy.account.declined")
            }
        }
    }

    private fun invitationMessage(id: String, name: String, handle: String, invitedBy: String): Component =
        Component.translatable("economy.lnbeconomy.account.invitation", invitedBy, name, Handles.display(handle)).withStyle(ChatFormatting.GOLD)
            .append(" ")
            .append(
                Component.translatable("economy.lnbeconomy.trade.accept").withStyle {
                    it.withColor(ChatFormatting.GREEN).withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account accept $id"))
                },
            )
            .append(" ")
            .append(
                Component.translatable("economy.lnbeconomy.trade.decline").withStyle {
                    it.withColor(ChatFormatting.RED).withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account decline $id"))
                },
            )

    private fun showInvitations(player: ServerPlayer, emptyMessage: Boolean) {
        val invitations = PlayerAccounts.get(player.uuid)?.invitations.orEmpty()
        if (invitations.isEmpty()) {
            if (emptyMessage) player.sendSystemMessage(Component.translatable("economy.lnbeconomy.account.no_invitations"))
            return
        }
        invitations.forEach { player.sendSystemMessage(invitationMessage(it.id, it.account.name, it.account.handle, it.invitedBy ?: "?")) }
    }

    /** Invitations sent while the player was away are shown when they join. */
    fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        PlayerAccounts.refresh(player.server, player.uuid, force = false).thenAccept {
            player.server.execute { if (!player.hasDisconnected()) showInvitations(player, emptyMessage = false) }
        }
    }

    // Requests from the screens

    fun handleCreate(payload: AccountCreatePayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        create(Reply(player, screen = true), payload.handle, payload.name, payload.type)
    }

    fun handleEdit(payload: AccountEditPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        edit(Reply(player, screen = true), payload.handle, payload.name, payload.type)
    }

    fun handleMembersRequest(payload: AccountMembersRequestPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        members(Reply(player, screen = true), payload.handle)
    }

    fun handleInvite(payload: AccountInvitePayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        invite(Reply(player, screen = true), payload.handle, payload.username)
    }

    fun handleCancel(payload: InvitationCancelPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        cancel(Reply(player, screen = true), payload.handle, payload.invitationId)
    }

    fun handleAnswer(payload: InvitationAnswerPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        answer(Reply(player, screen = true), payload.invitationId, payload.accept)
    }

    // Commands

    fun registerCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(commands("account"))
        event.dispatcher.register(commands("compte"))
    }

    private fun commands(name: String): LiteralArgumentBuilder<CommandSourceStack> {
        fun reply(context: CommandContext<CommandSourceStack>) = Reply(context.source.playerOrException, screen = false)
        fun text(context: CommandContext<CommandSourceStack>, argument: String) = StringArgumentType.getString(context, argument)

        /** A `handle` argument that suggests the player's own shared accounts, [ownedOnly] or all. */
        fun handle(ownedOnly: Boolean) = Commands.argument("handle", StringArgumentType.word()).suggests { context, builder ->
            suggest(builder, own(context).filter { !ownedOnly || it.role == AccountView.ROLE_OWNER }.mapNotNull { it.handle })
        }

        return Commands.literal(name)
            .then(
                Commands.literal("create").then(
                    Commands.argument("handle", StringArgumentType.word()).then(
                        Commands.argument("name", StringArgumentType.greedyString()).executes { context ->
                            create(reply(context), text(context, "handle"), text(context, "name"), AccountTypes.TEAM)
                            1
                        },
                    ),
                ),
            )
            .then(
                Commands.literal("rename").then(
                    handle(ownedOnly = true).then(
                        Commands.argument("name", StringArgumentType.greedyString()).executes { context ->
                            edit(reply(context), text(context, "handle"), text(context, "name"), null)
                            1
                        },
                    ),
                ),
            )
            .then(
                Commands.literal("type").then(
                    handle(ownedOnly = true).then(
                        Commands.argument("type", StringArgumentType.word())
                            .suggests { _, builder -> suggest(builder, AccountTypes.ALL) }
                            .executes { context ->
                                edit(reply(context), text(context, "handle"), null, text(context, "type"))
                                1
                            },
                    ),
                ),
            )
            .then(
                Commands.literal("invite").then(
                    handle(ownedOnly = true).then(
                        Commands.argument("player", StringArgumentType.word())
                            .suggests { context, builder ->
                                val self = context.source.player?.gameProfile?.name
                                suggest(builder, context.source.server.playerList.players.map { it.gameProfile.name }.filter { it != self })
                            }
                            .executes { context ->
                                invite(reply(context), text(context, "handle"), text(context, "player"))
                                1
                            },
                    ),
                ),
            )
            .then(
                Commands.literal("members").then(
                    handle(ownedOnly = false).executes { context ->
                        members(reply(context), text(context, "handle"))
                        1
                    },
                ),
            )
            .then(
                Commands.literal("invitations").executes { context ->
                    val player = context.source.playerOrException
                    PlayerAccounts.refresh(player.server, player.uuid, force = false).thenAccept {
                        player.server.execute { showInvitations(player, emptyMessage = true) }
                    }
                    1
                },
            )
            .then(
                Commands.literal("accept").then(
                    Commands.argument("invitation", StringArgumentType.word()).executes { context ->
                        answer(reply(context), text(context, "invitation"), accept = true)
                        1
                    },
                ),
            )
            .then(
                Commands.literal("decline").then(
                    Commands.argument("invitation", StringArgumentType.word()).executes { context ->
                        answer(reply(context), text(context, "invitation"), accept = false)
                        1
                    },
                ),
            )
    }

    private fun own(context: CommandContext<CommandSourceStack>): List<AccountView> =
        context.source.player?.let { PlayerAccounts.accounts(it.uuid)?.accounts }.orEmpty()

    private fun suggest(builder: SuggestionsBuilder, options: Collection<String>) =
        options.filter { it.startsWith(builder.remaining, ignoreCase = true) }.sorted().forEach(builder::suggest).let { builder.buildFuture() }
}
