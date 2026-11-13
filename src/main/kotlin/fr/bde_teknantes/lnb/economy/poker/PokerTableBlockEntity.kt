package fr.bde_teknantes.lnb.economy.poker

import fr.bde_teknantes.lnb.economy.EconomyConfig
import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.ModContent
import fr.bde_teknantes.lnb.economy.account.RealPlayers
import fr.bde_teknantes.lnb.economy.casino.CasinoBook
import fr.bde_teknantes.lnb.economy.casino.CasinoLinks
import fr.bde_teknantes.lnb.economy.casino.Casinos
import fr.bde_teknantes.lnb.economy.chip.ChipLedger
import fr.bde_teknantes.lnb.economy.chip.Chips
import fr.bde_teknantes.lnb.economy.mail.ItemMailbox
import fr.bde_teknantes.lnb.economy.money.Texts
import fr.bde_teknantes.lnb.economy.network.PokerSeatView
import fr.bde_teknantes.lnb.economy.network.PokerStatePayload
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.block.Block.UPDATE_CLIENTS
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID

/**
 * A poker table: its round ([HoldemRound] has the rules), the chips that go in and out, and the players watching it.
 *
 * Chips work as at every machine. An ante or a call destroys chips of the table's casino; a payout makes some. Before
 * an ante is taken, the casino's free balance must cover the most every seat could win, kept back until the round
 * ends. Nothing here calls the site.
 *
 * The stakes of a round in progress are saved with the block: after a restart or a crash mid-round they are given
 * back, since the cards are gone.
 */
class PokerTableBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModContent.POKER_ENTITY.get(), pos, state) {
    private data class TableKey(val dimension: String, val pos: Long)

    /** A stake to give back: who, how much, in which casino's chips. */
    private data class Owed(val player: UUID, val value: Long, val casino: String)

    private var round = HoldemRound { Cards.deck().shuffled(java.util.Random(level?.random?.nextLong() ?: System.nanoTime())) }

    /** The casino whose chips this round is played in: the link when its first ante was taken. */
    private var roundCasino: String? = null
    private val viewers = HashSet<UUID>()
    private var refunds: List<Owed> = emptyList()
    private var sinceSync = 0

    /** Game time when the round's script started, so clients play it at the same moment. */
    private var scriptBase = 0L

    /** What clients last got of the script: when it changes, they get it again. */
    private var scriptSent = ""

    /** The round as the client renders it: cards and gestures on the game clock. Null on the server. */
    var view: PokerTableView? = null
        private set

    private fun key(level: ServerLevel) = TableKey(level.dimension().location().toString(), blockPos.asLong())

    private fun link(level: ServerLevel): CasinoLinks.Link? = CasinoLinks.get(level.server).get(level.dimension(), blockPos)

    // What players do

    fun open(player: ServerPlayer) {
        val level = player.serverLevel()
        val link = link(level)
        if (link == null) {
            error(player, Component.translatable("economy.lnbeconomy.machine.not_linked"))
            return
        }
        Casinos.refresh(level.server, link.handle)
        viewers.add(player.uuid)
        PacketDistributor.sendToPlayer(player, state(level, open = true))
    }

    fun close(player: ServerPlayer) {
        viewers.remove(player.uuid)
    }

    fun sit(player: ServerPlayer, seat: Int, ante: Long) {
        val level = player.serverLevel()
        if (!RealPlayers.isReal(player)) return
        if (!EconomyConfig.gameEnabled(PokerTableBlock.GAME)) return error(player, Component.translatable("economy.lnbeconomy.machine.disabled"))
        val link = link(level) ?: return error(player, Component.translatable("economy.lnbeconomy.machine.not_linked"))
        val casino = link.handle
        val name = Casinos.name(casino)
        if (!round.canSit(seat, player.uuid)) return error(player, Component.translatable("economy.lnbeconomy.poker.cant_sit"))
        // A round is played in one casino's chips, whatever happens to the link meanwhile.
        if (round.seated.isNotEmpty() && roundCasino != casino) return error(player, Component.translatable("economy.lnbeconomy.poker.cant_sit"))
        if (ante < 1) return error(player, Component.translatable("economy.lnbeconomy.poker.no_ante"))
        link.maxBet?.let { if (ante > it) return error(player, Component.translatable("economy.lnbeconomy.machine.max_bet", name, Texts.amount(it, player))) }
        if (Chips.valueIn(player.inventory, casino) < ante) return error(player, Component.translatable("economy.lnbeconomy.poker.no_chips", Texts.amount(ante, player), name))

        val key = key(level)
        val exposure = (round.seated.sumOf { it.second.ante } + ante) * HoldemRules.MAX_NET_ANTES
        when (val status = Casinos.status(level.server, casino, except = key)) {
            is CasinoBook.Status.Open -> if (exposure > status.free) return error(player, Component.translatable("economy.lnbeconomy.machine.casino_short", name))
            CasinoBook.Status.Frozen -> return error(player, Component.translatable("economy.lnbeconomy.casino.frozen", name))
            CasinoBook.Status.NotCasino -> return error(player, Component.translatable("economy.lnbeconomy.casino.not_casino", name))
            CasinoBook.Status.Missing -> return error(player, Component.translatable("economy.lnbeconomy.casino.missing", name))
            CasinoBook.Status.Unknown -> return error(player, Component.translatable("economy.lnbeconomy.machine.casino_unknown", name))
        }
        if (!take(player, casino, ante)) return
        Casinos.book.reserve(key, casino, exposure, RESERVATION_TTL_MS)
        roundCasino = casino
        round.sit(seat, player.uuid, player.gameProfile.name, ante)
        level.playSound(null, blockPos, SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.BLOCKS, 0.7f, 1.4f)
        changed(level)
    }

    fun call(player: ServerPlayer) {
        val level = player.serverLevel()
        val casino = roundCasino ?: return
        val seat = round.seat(round.seatOf(player.uuid)) ?: return
        if (!round.canDecide(player.uuid)) return
        val cost = seat.ante * HoldemRules.CALL_ANTES
        if (Chips.valueIn(player.inventory, casino) < cost) {
            return error(player, Component.translatable("economy.lnbeconomy.poker.no_chips", Texts.amount(cost, player), Casinos.name(casino)))
        }
        if (!take(player, casino, cost)) return
        round.call(player.uuid)
        level.playSound(null, blockPos, SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.BLOCKS, 0.7f, 1.2f)
        changed(level)
    }

    fun fold(player: ServerPlayer) {
        if (round.fold(player.uuid)) changed(player.serverLevel())
    }

    /** Destroys [value] of the player's chips of [casino]: takes them all, and hands back the change. */
    private fun take(player: ServerPlayer, casino: String, value: Long): Boolean {
        val held = Chips.takeAll(player.inventory, casino)
        if (held < value) {
            ItemMailbox.deliverChips(player.server, player.uuid, casino, held)
            return false
        }
        if (held > value) ItemMailbox.deliverChips(player.server, player.uuid, casino, held - value)
        ChipLedger.get(player.server).add(casino, -value)
        return true
    }

    // The round

    fun serverTick(level: ServerLevel) {
        if (refunds.isNotEmpty()) {
            val owed = refunds
            refunds = emptyList()
            owed.forEach { giveBack(level, it) }
            setChanged()
        }
        val phase = round.phase
        val settled = round.tick()
        scriptBase = level.gameTime - round.now
        if (settled != null) settle(level, settled)
        syncScript(level)
        if (round.phase != phase || settled != null) {
            if (round.phase == HoldemRound.Phase.DECISION) level.playSound(null, blockPos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 1.3f)
            if (round.phase == HoldemRound.Phase.WAITING) roundCasino = null
            changed(level)
        } else if (++sinceSync >= 20 && round.phase != HoldemRound.Phase.WAITING) {
            sync(level) // the countdown
        } else if (round.phase == HoldemRound.Phase.DEALING || round.phase == HoldemRound.Phase.SHOWDOWN) {
            // Cards land one by one: the screen follows the table.
            if (round.now % 11 == 0) sync(level)
        }
    }

    private fun settle(level: ServerLevel, settled: HoldemRound.Settled) {
        val casino = roundCasino ?: return
        val server = level.server
        Casinos.book.release(key(level))
        for ((_, seat) in settled.seats) {
            if (seat.payout > 0) {
                ChipLedger.get(server).add(casino, seat.payout)
                ItemMailbox.deliverChips(server, seat.player, casino, seat.payout)
            }
            val player = server.playerList.getPlayer(seat.player) ?: continue
            val result = seat.result ?: continue
            val won = seat.payout > seat.staked
            val message = Component.translatable(
                "economy.lnbeconomy.poker.result.${result.name.lowercase()}", Texts.amount(seat.payout, player), Texts.amount(seat.staked, player),
            )
            player.sendSystemMessage(message.withStyle(if (won) ChatFormatting.GREEN else if (seat.payout > 0) ChatFormatting.YELLOW else ChatFormatting.GRAY))
            if (won) player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f)
        }
        LnbEconomy.LOGGER.info(
            "Poker at {} for #{}: {}", blockPos, casino,
            settled.seats.joinToString { (_, seat) -> "${seat.name} staked ${seat.staked}, ${seat.result}, paid ${seat.payout}" },
        )
    }

    /** The table is being removed: every seat of a round that isn't settled gets its stakes back. */
    fun abort(level: ServerLevel) {
        val casino = roundCasino
        val owed = round.abort()
        Casinos.book.release(key(level))
        if (casino != null) owed.forEach { giveBack(level, Owed(it.player, it.staked, casino)) }
        roundCasino = null
        viewers.clear()
    }

    private fun giveBack(level: ServerLevel, owed: Owed) {
        if (owed.value <= 0) return
        ChipLedger.get(level.server).add(owed.casino, owed.value)
        ItemMailbox.deliverChips(level.server, owed.player, owed.casino, owed.value)
        level.server.playerList.getPlayer(owed.player)?.sendSystemMessage(
            Component.translatable("economy.lnbeconomy.poker.refunded", Texts.amount(owed.value, level.server.playerList.getPlayer(owed.player))),
        )
        LnbEconomy.LOGGER.info("Poker at {}: gave {} of #{} back to {}", blockPos, owed.value, owed.casino, owed.player)
    }

    // What players see

    /** Sends the round's script to every client that sees the table when it changes: a deal, a showdown, a clear. */
    private fun syncScript(level: ServerLevel) {
        val script = round.script
        val signature = "${round.phase}|${script.cards.size}|${script.gestures.size}|${script.watchSeat}|${script.watchTo}"
        if (signature == scriptSent) return
        scriptSent = signature
        level.sendBlockUpdated(blockPos, blockState, blockState, UPDATE_CLIENTS)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        val script = round.script
        val tag = CompoundTag()
        tag.putLong(VIEW_BASE, scriptBase)
        // The dealer's cards go out face down until the showdown has them turned.
        tag.putIntArray(VIEW_CARDS, script.cards.flatMap { listOf(it.seat, it.index, if (it.seat == HoldemRound.DEALER && it.flipAt < 0) Cards.HIDDEN else it.value, it.dealtAt, it.flipAt) })
        tag.putIntArray(VIEW_GESTURES, script.gestures.flatMap { listOf(it.gesture.ordinal, it.at, it.seat, it.index) })
        tag.putIntArray(VIEW_TIMES, intArrayOf(script.clearAt, script.watchFrom, script.watchTo, script.watchSeat))
        return tag
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    /** On the client: the script the server sent with the block. */
    private fun readView(tag: CompoundTag) {
        val times = tag.getIntArray(VIEW_TIMES)
        view = PokerTableView.read(tag.getLong(VIEW_BASE), tag.getIntArray(VIEW_CARDS), tag.getIntArray(VIEW_GESTURES), times)
    }

    private fun changed(level: ServerLevel) {
        setChanged()
        sync(level)
    }

    private fun sync(level: ServerLevel) {
        sinceSync = 0
        if (viewers.isEmpty()) return
        val payload = state(level, open = false)
        val gone = ArrayList<UUID>()
        for (uuid in viewers) {
            val player = level.server.playerList.getPlayer(uuid)
            // A seated player keeps getting the round from anywhere; a bystander who walked off stops watching.
            if (player == null || (round.seatOf(uuid) < 0 && (player.level() !== level || player.distanceToSqr(blockPos.center) > WATCH_RANGE * WATCH_RANGE))) {
                gone.add(uuid)
            } else {
                PacketDistributor.sendToPlayer(player, payload)
            }
        }
        viewers.removeAll(gone.toSet())
    }

    private fun state(level: ServerLevel, open: Boolean): PokerStatePayload {
        val link = link(level)
        val casino = roundCasino ?: link?.handle.orEmpty()
        val shown = round.phase == HoldemRound.Phase.SHOWDOWN && round.resultsOut
        val seats = (0 until HoldemRound.SEATS).map { index ->
            round.seat(index)?.let { seat ->
                // A seat's cards show once the dealer has put them down.
                val dealt = round.script.cards.filter { it.seat == index && it.dealtAt + CARD_LANDS <= round.now }.sortedBy { it.index }.map { it.value }
                PokerSeatView(
                    seat.name, seat.ante, dealt, seat.choice.ordinal, if (shown) seat.result?.ordinal ?: -1 else -1, if (shown) seat.payout else 0,
                    if (shown && seat.choice == HoldemRound.Choice.CALL) Hands.category(seat.score) else -1,
                )
            }
        }
        return PokerStatePayload(
            blockPos, casino, Casinos.name(casino), round.phase.ordinal, round.ticksLeft,
            round.script.cards.filter { it.seat == HoldemRound.BOARD && it.dealtAt + CARD_LANDS <= round.now }.sortedBy { it.index }.map { it.value },
            round.dealerShown.take(round.script.cards.count { it.seat == HoldemRound.DEALER && it.dealtAt + CARD_LANDS <= round.now }),
            if (shown && round.board.size == 5) Hands.category(Hands.score(round.dealer + round.board)) else -1,
            seats, link?.maxBet ?: 0, open,
        )
    }

    private fun error(player: ServerPlayer, message: Component) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), false)
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1f, 0.5f)
    }

    // Saved with the block: only what a lost round owes

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        val casino = roundCasino
        val owed = refunds + if (casino == null || round.phase == HoldemRound.Phase.SHOWDOWN) emptyList() else round.seated.map { Owed(it.second.player, it.second.staked, casino) }
        if (owed.isEmpty()) return
        tag.put(OWED, ListTag().apply {
            owed.forEach {
                add(CompoundTag().apply {
                    putUUID("player", it.player)
                    putLong("value", it.value)
                    putString("casino", it.casino)
                })
            }
        })
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        if (tag.contains(VIEW_CARDS)) {
            readView(tag)
            return
        }
        val list = tag.getList(OWED, Tag.TAG_COMPOUND.toInt())
        refunds = (0 until list.size).map(list::getCompound).filter { it.hasUUID("player") }
            .map { Owed(it.getUUID("player"), it.getLong("value"), it.getString("casino")) }
    }

    private companion object {
        const val OWED = "owed"
        const val VIEW_BASE = "viewBase"
        const val VIEW_CARDS = "viewCards"
        const val VIEW_GESTURES = "viewGestures"
        const val VIEW_TIMES = "viewTimes"

        /** A dealt card flies for this long before it lies on the table (MineVegas's card motion). */
        const val CARD_LANDS = 23
        const val WATCH_RANGE = 12.0

        /** A round lasts well under this; past it, a table that stopped ticking no longer holds the casino's money. */
        const val RESERVATION_TTL_MS = 5 * 60_000L
    }
}
