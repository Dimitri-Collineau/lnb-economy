package fr.bde_teknantes.lnb.economy.casino

import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.ModContent
import fr.bde_teknantes.lnb.economy.account.PlayerAccounts
import fr.bde_teknantes.lnb.economy.api.AccountView
import fr.bde_teknantes.lnb.economy.api.Handles
import fr.bde_teknantes.lnb.economy.api.TransferBody
import fr.bde_teknantes.lnb.economy.counter.CounterBlock
import fr.bde_teknantes.lnb.economy.counter.CounterWood
import fr.bde_teknantes.lnb.economy.network.CounterWoodPayload
import fr.bde_teknantes.lnb.economy.network.LinkOption
import fr.bde_teknantes.lnb.economy.network.LinkPayload
import fr.bde_teknantes.lnb.economy.network.OpenLinkPayload
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Linking machines and counters to a casino with the link wand. Only an owner or member of a casino's account may link
 * something to it, checked with the site at that moment; an existing link is changed by an owner or member of its
 * casino, or an operator.
 */
object Links {
    private const val OPERATOR = 2
    private const val REACH = 8.0

    /** A machine's anchor (with its game) or a counter, at or around a clicked block. */
    data class Target(val pos: BlockPos, val game: String?)

    fun targetAt(level: Level, pos: BlockPos): Target? {
        val state = level.getBlockState(pos)
        if (state.`is`(ModContent.COUNTER.get())) return Target(CounterBlock.masterPos(state, pos), null)
        val support = Machines.support ?: return null
        val anchor = support.anchorAt(level, pos) ?: return null
        return Target(anchor, support.gameAt(level, anchor))
    }

    /** Right click with the wand: read the player's accounts again, then show the link screen. */
    fun open(player: ServerPlayer, target: Target) {
        val server = player.server
        PlayerAccounts.refresh(server, player.uuid).thenAccept {
            val accounts = PlayerAccounts.accounts(player.uuid)
            if (accounts == null) {
                val message = PlayerAccounts.noEconomyMessage(player.uuid) ?: Component.translatable("economy.lnbeconomy.problem.unavailable")
                player.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED))
                return@thenAccept
            }
            val link = CasinoLinks.get(server).get(player.level().dimension(), target.pos)
            val options = accounts.accounts.filter(::mayLinkTo).map { LinkOption(it.handle!!, it.name ?: it.handle) }
            if (options.isEmpty() && link == null) {
                player.sendSystemMessage(Component.translatable("economy.lnbeconomy.link.no_casino").withStyle(ChatFormatting.RED))
                return@thenAccept
            }
            val mayEdit = link == null || mayEdit(player, accounts.accounts, link)
            val counter = player.level().getBlockState(target.pos).takeIf { target.game == null && it.`is`(ModContent.COUNTER.get()) }
            val wood = counter?.takeIf { mayEdit }?.getValue(CounterBlock.WOOD)?.serializedName
            PacketDistributor.sendToPlayer(
                player,
                OpenLinkPayload(target.pos, target.game, link?.handle, link?.maxBet, link != null && mayEdit, if (mayEdit) options else emptyList(), wood),
            )
        }
    }

    fun handleLink(payload: LinkPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        val level = player.serverLevel()
        if (!level.isLoaded(payload.pos) || player.distanceToSqr(payload.pos.center) > REACH * REACH) return
        val target = targetAt(level, payload.pos)?.takeIf { it.pos == payload.pos } ?: return
        val maxBet = payload.maxBet?.takeIf { target.game != null && it in 1..TransferBody.MAX_AMOUNT }
        val server = player.server

        // Roles are checked against the site right now, not against what was true when the screen opened.
        PlayerAccounts.refresh(server, player.uuid).thenAccept {
            val accounts = PlayerAccounts.accounts(player.uuid)?.accounts
            if (accounts == null) {
                player.sendSystemMessage(Component.translatable("economy.lnbeconomy.problem.unavailable").withStyle(ChatFormatting.RED))
                return@thenAccept
            }
            val links = CasinoLinks.get(server)
            val existing = links.get(level.dimension(), target.pos)
            if (existing != null && !mayEdit(player, accounts, existing)) {
                player.sendSystemMessage(Component.translatable("economy.lnbeconomy.link.not_yours", Handles.display(existing.handle)).withStyle(ChatFormatting.RED))
                return@thenAccept
            }
            val handle = payload.handle?.let(Handles::bare)
            if (handle == null) {
                if (existing != null) {
                    links.remove(level.dimension(), target.pos)
                    LnbEconomy.LOGGER.info("{} unlinked {} at {} from #{}", player.gameProfile.name, target.game ?: "counter", target.pos, existing.handle)
                    player.sendSystemMessage(Component.translatable("economy.lnbeconomy.link.removed"))
                }
                return@thenAccept
            }
            if (accounts.none { it.handle == handle && mayLinkTo(it) }) {
                player.sendSystemMessage(Component.translatable("economy.lnbeconomy.link.not_member", Handles.display(handle)).withStyle(ChatFormatting.RED))
                return@thenAccept
            }
            links.put(level.dimension(), target.pos, CasinoLinks.Link(handle, maxBet, player.uuid, player.gameProfile.name))
            Casinos.refresh(server, handle)
            LnbEconomy.LOGGER.info("{} linked {} at {} to #{}", player.gameProfile.name, target.game ?: "counter", target.pos, handle)
            player.sendSystemMessage(Component.translatable("economy.lnbeconomy.link.done", Handles.display(handle)).withStyle(ChatFormatting.GREEN))
        }
    }

    /**
     * Rebuilds a counter's wall in another wood. Allowed to whoever may edit its link: an owner or member of its casino,
     * or an operator; for a counter not linked yet, anyone who could link it to one of their casinos.
     */
    fun handleWood(payload: CounterWoodPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        val level = player.serverLevel()
        val wood = CounterWood.byId(payload.wood) ?: return
        if (!level.isLoaded(payload.pos) || player.distanceToSqr(payload.pos.center) > REACH * REACH) return
        val master = level.getBlockState(payload.pos)
        if (!master.`is`(ModContent.COUNTER.get()) || master.getValue(CounterBlock.PART) != CounterBlock.MASTER) return
        val server = player.server
        PlayerAccounts.refresh(server, player.uuid).thenAccept {
            val accounts = PlayerAccounts.accounts(player.uuid)?.accounts
            if (accounts == null) {
                player.sendSystemMessage(Component.translatable("economy.lnbeconomy.problem.unavailable").withStyle(ChatFormatting.RED))
                return@thenAccept
            }
            val link = CasinoLinks.get(server).get(level.dimension(), payload.pos)
            val allowed = if (link != null) mayEdit(player, accounts, link) else player.hasPermissions(OPERATOR) || accounts.any(::mayLinkTo)
            if (!allowed) {
                player.sendSystemMessage(Component.translatable("economy.lnbeconomy.link.not_yours", Handles.display(link?.handle ?: "")).withStyle(ChatFormatting.RED))
                return@thenAccept
            }
            val current = level.getBlockState(payload.pos)
            if (!current.`is`(ModContent.COUNTER.get())) return@thenAccept
            for ((_, p) in CounterBlock.allParts(payload.pos, current.getValue(CounterBlock.FACING))) {
                val part = level.getBlockState(p)
                if (part.`is`(ModContent.COUNTER.get())) level.setBlock(p, part.setValue(CounterBlock.WOOD, wood), Block.UPDATE_CLIENTS)
            }
        }
    }

    /** A machine broken by a player loses its link, so a new machine placed there starts unlinked. */
    fun onBlockBroken(event: BlockEvent.BreakEvent) {
        val level = event.level as? net.minecraft.server.level.ServerLevel ?: return
        val anchor = Machines.support?.anchorAt(level, event.pos) ?: return
        if (CasinoLinks.get(level.server).remove(level.dimension(), anchor) != null) {
            LnbEconomy.LOGGER.info("The machine at {} was broken: its casino link is removed", anchor)
        }
    }

    private fun mayLinkTo(account: AccountView): Boolean =
        account.handle != null && (account.role == AccountView.ROLE_OWNER || account.role == AccountView.ROLE_MEMBER)

    private fun mayEdit(player: ServerPlayer, accounts: List<AccountView>, link: CasinoLinks.Link): Boolean =
        player.hasPermissions(OPERATOR) || accounts.any { it.handle == link.handle && mayLinkTo(it) }
}
