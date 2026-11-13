package fr.bde_teknantes.lnb.economy.poker

import java.util.UUID

/**
 * One poker table's game, without Minecraft or money: who sits where, the cards, and what each tick changes. Up to
 * [SEATS] players play the same round, each with their own two cards and ante, against one dealer and one board.
 *
 * A round goes: [Phase.WAITING] until someone antes; [Phase.BETTING], a short while for others to join; then the
 * cards are dealt and [Phase.DECISION] lets every seat call or fold (silence folds); [Phase.SHOWDOWN] turns the last
 * cards and holds the results on the table; then the seats empty and it waits again.
 *
 * The caller moves the chips: it checks them before [sit] and [call], and pays what [tick] reports in [Settled].
 */
class HoldemRound(private val shuffled: () -> List<Int>) {
    enum class Phase { WAITING, BETTING, DECISION, SHOWDOWN }

    enum class Choice { NONE, CALL, FOLD }

    class Seat(val player: UUID, val name: String, val ante: Long) {
        var cards: List<Int> = emptyList()
            internal set
        var choice: Choice = Choice.NONE
            internal set
        var result: HoldemRules.Result? = null
            internal set
        var payout: Long = 0
            internal set

        /** What this seat has put on the table so far. */
        val staked: Long get() = if (choice == Choice.CALL) ante * (1 + HoldemRules.CALL_ANTES) else ante
    }

    /** The round is decided: what each seat gets back. Reported once, by the [tick] that ends the decisions. */
    class Settled(val seats: List<Pair<Int, Seat>>)

    private val seats = arrayOfNulls<Seat>(SEATS)

    var phase: Phase = Phase.WAITING
        private set

    /** Ticks left in the current phase; 0 while waiting. */
    var ticksLeft: Int = 0
        private set
    var board: List<Int> = emptyList()
        private set
    var dealer: List<Int> = emptyList()
        private set
    private var deck: List<Int> = emptyList()

    fun seat(index: Int): Seat? = seats.getOrNull(index)

    fun seatOf(player: UUID): Int = seats.indexOfFirst { it?.player == player }

    val seated: List<Pair<Int, Seat>> get() = seats.mapIndexedNotNull { i, seat -> seat?.let { i to it } }

    /** The dealer's cards as the table shows them: face down until the showdown. */
    val dealerShown: List<Int> get() = if (phase == Phase.SHOWDOWN) dealer else dealer.map { Cards.HIDDEN }

    fun canSit(index: Int, player: UUID): Boolean =
        (phase == Phase.WAITING || phase == Phase.BETTING) && index in 0 until SEATS && seats[index] == null && seatOf(player) < 0

    /** Seats [player] with their [ante]. The first ante starts the countdown to the deal. */
    fun sit(index: Int, player: UUID, name: String, ante: Long): Boolean {
        if (ante < 1 || !canSit(index, player)) return false
        seats[index] = Seat(player, name, ante)
        if (phase == Phase.WAITING) {
            phase = Phase.BETTING
            ticksLeft = BETTING_TICKS
        }
        // A full table has nobody left to wait for.
        if (seats.all { it != null }) ticksLeft = minOf(ticksLeft, FULL_TABLE_TICKS)
        return true
    }

    fun canDecide(player: UUID): Boolean = phase == Phase.DECISION && seats.getOrNull(seatOf(player))?.choice == Choice.NONE

    fun call(player: UUID): Boolean = decide(player, Choice.CALL)

    fun fold(player: UUID): Boolean = decide(player, Choice.FOLD)

    private fun decide(player: UUID, choice: Choice): Boolean {
        if (!canDecide(player)) return false
        seats[seatOf(player)]!!.choice = choice
        return true
    }

    /** One game tick. Returns the round's outcome on the tick that settles it, else null. */
    fun tick(): Settled? {
        if (phase == Phase.WAITING) return null
        if (phase == Phase.DECISION && seated.all { it.second.choice != Choice.NONE }) ticksLeft = 0
        if (ticksLeft > 0) ticksLeft--
        if (ticksLeft > 0) return null
        return when (phase) {
            Phase.BETTING -> {
                deal()
                null
            }
            Phase.DECISION -> settle()
            Phase.SHOWDOWN -> {
                clear()
                null
            }
            Phase.WAITING -> null
        }
    }

    private fun deal() {
        deck = shuffled()
        var next = 0
        for ((_, seat) in seated) {
            seat.cards = deck.subList(next, next + 2)
            next += 2
        }
        dealer = deck.subList(next, next + 2)
        board = deck.subList(next + 2, next + 5)
        deck = deck.subList(next + 5, deck.size)
        phase = Phase.DECISION
        ticksLeft = DECISION_TICKS
    }

    private fun settle(): Settled {
        board = board + deck.take(2)
        val dealerScore = Hands.score(dealer + board)
        for ((_, seat) in seated) {
            if (seat.choice != Choice.CALL) {
                seat.choice = Choice.FOLD
                seat.result = HoldemRules.Result.FOLD
                continue
            }
            val score = Hands.score(seat.cards + board)
            val result = HoldemRules.result(score, dealerScore)
            seat.result = result
            seat.payout = HoldemRules.payout(seat.ante, result, score)
        }
        phase = Phase.SHOWDOWN
        ticksLeft = SHOWDOWN_TICKS
        return Settled(seated)
    }

    private fun clear() {
        seats.fill(null)
        board = emptyList()
        dealer = emptyList()
        deck = emptyList()
        phase = Phase.WAITING
        ticksLeft = 0
    }

    /** Stops a round that can't go on (the table is broken): every seat's stakes, to give back. The table is left empty. */
    fun abort(): List<Seat> {
        // A settled round has been paid already: nothing more is owed.
        val owed = if (phase == Phase.SHOWDOWN) emptyList() else seated.map { it.second }
        clear()
        return owed
    }

    companion object {
        const val SEATS = 3
        const val BETTING_TICKS = 200
        const val FULL_TABLE_TICKS = 40
        const val DECISION_TICKS = 500
        const val SHOWDOWN_TICKS = 160
    }
}
