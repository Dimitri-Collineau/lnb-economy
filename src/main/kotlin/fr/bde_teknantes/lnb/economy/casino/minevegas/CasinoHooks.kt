package fr.bde_teknantes.lnb.economy.casino.minevegas

import fr.bde_teknantes.lnb.economy.EconomyConfig
import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.casino.CasinoBook
import fr.bde_teknantes.lnb.economy.casino.CasinoLinks
import fr.bde_teknantes.lnb.economy.casino.Casinos
import fr.bde_teknantes.lnb.economy.casino.Exposure
import fr.bde_teknantes.lnb.economy.casino.MenuCasinos
import fr.bde_teknantes.lnb.economy.chip.ChipItem
import fr.bde_teknantes.lnb.economy.chip.ChipLedger
import fr.bde_teknantes.lnb.economy.chip.ChipPayout
import fr.bde_teknantes.lnb.economy.chip.Chips
import fr.bde_teknantes.lnb.economy.money.Texts
import fr.minevegas.casino.CasinoMachine
import fr.minevegas.casino.CasinoSettings
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.TickTask
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Container
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import java.util.function.Supplier

/**
 * What the MineVegas mixins call. Machines only take chips of the casino they are linked to; a game starts only when
 * that casino's free balance covers the most chips it can create; stakes destroyed and payouts made update the
 * casino's chips in circulation. Nothing here calls the site.
 */
object CasinoHooks {
    /** A round that never ends (a table broken mid-round) stops holding the casino's money after this. */
    private const val RESERVATION_TTL_MS = 10 * 60_000L

    private data class MachineKey(val dimension: String, val pos: Long)

    private fun keyOf(machine: BlockEntity) = MachineKey(machine.level!!.dimension().location().toString(), machine.blockPos.asLong())

    private fun linkOf(machine: BlockEntity): CasinoLinks.Link? {
        val level = machine.level as? ServerLevel ?: return null
        return CasinoLinks.get(level.server).get(level.dimension(), machine.blockPos)
    }

    // Which items are bets

    /**
     * Any chip, for the lists and settings screens that don't know their machine: they test plain item stacks, so a
     * chip without its casino must pass. Actual bets always go through [accepts] or [acceptsInMenu].
     */
    @JvmStatic
    fun isBettable(stack: ItemStack): Boolean = Chips.isChip(stack)

    /** Only chips of the machine's casino. On the client, where links aren't known, any chip. */
    @JvmStatic
    fun accepts(machine: BlockEntity, stack: ItemStack): Boolean {
        val casino = Chips.casinoOf(stack) ?: return false
        if (machine.level?.isClientSide != false) return true
        return casino == linkOf(machine)?.handle
    }

    /** The bet slots of a casino menu: chips of its machine's casino, told by the server on the client. */
    @JvmStatic
    fun acceptsInMenu(menu: AbstractContainerMenu, stack: ItemStack): Boolean {
        val casino = Chips.casinoOf(stack) ?: return false
        val machine = CasinoSettings.menuMachine(menu)
        if (machine != null && machine.level?.isClientSide == false) return casino == linkOf(machine)?.handle
        return casino == MenuCasinos.casinoOf(menu.containerId)
    }

    // Checks before a game. They return false to refuse it, before anything is taken.

    /** Slots, wheel, Plinko, legacy Derby: every stake of [bets] can win [numerator]/[denominator]. */
    @JvmStatic
    fun allowPerStake(machine: BlockEntity, bets: Container, player: ServerPlayer?, numerator: Int, denominator: Int): Boolean {
        val stakes = stakesOf(bets)
        if (stakes.isEmpty()) return true
        return allow(machine, player, stakes, Exposure.perStake(stakes.map(::stakeOf), numerator, denominator), reserve = false)
    }

    @JvmStatic
    fun allowRoulette(machine: BlockEntity, bets: Container, player: ServerPlayer?, stakedColor: Int): Boolean {
        // No bet, or bets on several colours: MineVegas refuses the spin by itself.
        if (stakedColor < 0) return true
        val multiplier = if (stakedColor == MaxPayouts.GREEN) MaxPayouts.ROULETTE_GREEN else MaxPayouts.ROULETTE_COLOR
        return allowPerStake(machine, bets, player, multiplier, 1)
    }

    /**
     * Derby races, which run over several ticks: [runners] are every participant, the new one included, as
     * (lane, stakes). [newStakes] are the stakes this player adds, checked against the largest bet.
     */
    @JvmStatic
    fun allowDerby(machine: BlockEntity, player: ServerPlayer?, runners: List<Pair<Int, List<ItemStack>>>, newStakes: List<ItemStack>): Boolean {
        if (newStakes.isEmpty()) return true
        val exposure = Exposure.derby(runners.map { (lane, stakes) -> Exposure.Runner(lane, stakes.map(::stakeOf)) }, MaxPayouts.DERBY_WIN, MaxPayouts.DERBY_SECOND)
        return allow(machine, player, newStakes, exposure, reserve = true)
    }

    /** Blackjack and poker, settled rounds later. See [Exposure.table]. */
    @JvmStatic
    fun allowTable(machine: BlockEntity, player: ServerPlayer?, wager: List<ItemStack>, raise: Int, plain: Int, raised: Int, denominator: Int): Boolean {
        if (wager.isEmpty()) return true
        val exposure = Exposure.table(wager.map(::stakeOf), raise, plain, raised, denominator)
        return allow(machine, player, wager, exposure, reserve = true)
    }

    /** A round over several ticks has ended: it no longer holds back the casino's money. */
    @JvmStatic
    fun release(machine: BlockEntity) {
        if (machine.level is ServerLevel) Casinos.book.release(keyOf(machine))
    }

    private fun allow(machine: BlockEntity, player: ServerPlayer?, stakes: List<ItemStack>, exposure: Long, reserve: Boolean): Boolean {
        val level = machine.level as? ServerLevel ?: return true
        val game = CasinoMachine.type(machine)
        if (!EconomyConfig.gameEnabled(game)) return refuse(player, Component.translatable("economy.lnbeconomy.machine.disabled"))
        val link = linkOf(machine) ?: return refuse(player, Component.translatable("economy.lnbeconomy.machine.not_linked"))
        if (stakes.any { Chips.casinoOf(it) != link.handle }) return refuse(player, Component.translatable("economy.lnbeconomy.machine.wrong_chips", Casinos.name(link.handle)))

        val total = stakes.sumOf(Chips::valueOf)
        val maxBet = link.maxBet
        if (maxBet != null && total > maxBet) {
            return refuse(player, Component.translatable("economy.lnbeconomy.machine.max_bet", Casinos.name(link.handle), Texts.amount(maxBet, player)))
        }
        val key = keyOf(machine)
        return when (val status = Casinos.status(level.server, link.handle, except = key)) {
            is CasinoBook.Status.Open -> if (exposure > status.free) {
                refuse(player, Component.translatable("economy.lnbeconomy.machine.casino_short", Casinos.name(link.handle)))
            } else {
                if (reserve) Casinos.book.reserve(key, link.handle, maxOf(0, exposure), RESERVATION_TTL_MS)
                true
            }
            CasinoBook.Status.Frozen -> refuse(player, Component.translatable("economy.lnbeconomy.casino.frozen", Casinos.name(link.handle)))
            CasinoBook.Status.NotCasino -> refuse(player, Component.translatable("economy.lnbeconomy.casino.not_casino", Casinos.name(link.handle)))
            CasinoBook.Status.Missing -> refuse(player, Component.translatable("economy.lnbeconomy.casino.missing", Casinos.name(link.handle)))
            CasinoBook.Status.Unknown -> refuse(player, Component.translatable("economy.lnbeconomy.machine.casino_unknown", Casinos.name(link.handle)))
        }
    }

    /**
     * Tells the player why the game didn't start: in red above the hotbar, with a low note, and the machine's screen
     * closes so the message shows. The screen closes on the next tick, once MineVegas is done with the click.
     */
    private fun refuse(player: ServerPlayer?, message: Component): Boolean {
        if (player == null) return false
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true)
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1f, 0.5f)
        val server = player.server
        server.tell(TickTask(server.tickCount) {
            if (player.containerMenu !== player.inventoryMenu) player.closeContainer()
        })
        return false
    }

    // Chips destroyed and made by the games

    /** A stake was taken and destroyed by MineVegas. */
    @JvmStatic
    fun destroyed(machine: BlockEntity, stake: ItemStack) = destroyedAll(machine, listOf(stake))

    @JvmStatic
    fun destroyedAll(machine: BlockEntity, stakes: List<ItemStack>) = record(machine, stakes, -1)

    /**
     * A game's payout. For a chip stake, pays its value times the multiplier in chips of the same casino (see
     * [ChipPayout]); a refund gives the stake back as it was. Anything else keeps MineVegas's payout, [original].
     */
    @JvmStatic
    fun payout(stake: ItemStack, numerator: Int, denominator: Int, roll: Int, refund: Boolean, original: Supplier<List<ItemStack>>): List<ItemStack> {
        val casino = Chips.casinoOf(stake) ?: return original.get()
        if (refund) return listOf(stake.copy())
        return Chips.make(casino, ChipPayout.value(Chips.valueOf(stake), numerator, denominator, roll))
    }

    /** MineVegas made these chips as a payout. Returns them unchanged. */
    @JvmStatic
    fun created(machine: BlockEntity, payout: List<ItemStack>): List<ItemStack> {
        record(machine, payout, 1)
        return payout
    }

    /** A Derby escrow is settled: its stakes are destroyed and the reward is made. */
    @JvmStatic
    fun settleDerby(machine: BlockEntity, stakes: List<ItemStack>, reward: List<ItemStack>): List<ItemStack> {
        destroyedAll(machine, stakes)
        return created(machine, reward)
    }

    private fun record(machine: BlockEntity, stacks: List<ItemStack>, sign: Int) {
        val level = machine.level as? ServerLevel ?: return
        val ledger = ChipLedger.get(level.server)
        for (stack in stacks) {
            val casino = Chips.casinoOf(stack)
            if (casino == null) {
                if (!stack.isEmpty) LnbEconomy.LOGGER.warn("{} at {} moved {} x {}, which isn't a chip", CasinoMachine.type(machine), machine.blockPos, stack.count, stack.item)
                continue
            }
            ledger.add(casino, sign * Chips.valueOf(stack))
        }
    }

    // Helpers

    /** Non-empty stacks of a bet container, copied. */
    @JvmStatic
    fun stakesOf(bets: Container): List<ItemStack> =
        (0 until bets.containerSize).map(bets::getItem).filter { !it.isEmpty }.map(ItemStack::copy)

    /** Stakes of a table's bet container that the machine takes. */
    @JvmStatic
    fun acceptedStakesOf(machine: BlockEntity, bets: Container): List<ItemStack> = stakesOf(bets).filter { accepts(machine, it) }

    private fun stakeOf(stack: ItemStack): Exposure.Stake = Exposure.Stake((stack.item as? ChipItem)?.value ?: 0, stack.count)
}
