package fr.bde_teknantes.lnb.economy.poker

import java.util.UUID

/**
 * One poker table's game, without Minecraft or money: who sits where, the cards, and what each tick changes. Up to
 * [SEATS] players play the same round, each with their own two cards and ante, against one dealer and one board.
 *
 * A round goes: [Phase.WAITING] until someone antes; [Phase.BETTING], a short while for others to join; then
 * [Phase.DEALING]: the dealer shuffles and deals the cards one by one, as MineVegas Casino's dealer does, two to each
 * seat and to himself, then three to the board; [Phase.DECISION] lets every seat call or fold (silence folds);
 * [Phase.SHOWDOWN] deals the last two board cards, turns the dealer's, pays, and clears the table; then it waits again.
 *
 * Everything the table shows happens on a [script]: each card and each of the dealer's gestures at a tick counted from
 * the start of the deal, so every client animates the same round at the same moment.
 *
 * The caller moves the chips: it checks them before [sit] and [call], and pays what [tick] reports in [Settled].
 */
class HoldemRound(private val shuffled: () -> List<Int>) {
    enum class Phase { WAITING, BETTING, DEALING, DECISION, SHOWDOWN }

    enum class Choice { NONE, CALL, FOLD }

    /** The dealer's gestures, named as MineVegas Casino's dealer animations. */
    enum class Gesture { SHUFFLE, DEAL, FLIP, PAY, COLLECT, PUSH, CELEBRATE, CLEAR }

    class Seat(val player: UUID, val name: String, val ante: Long) {
        var cards: List<Int> = emptyList()
            internal set
        var choice: Choice = Choice.NONE
            internal set
        var result: HoldemRules.Result? = null
            internal set
        var payout: Long = 0
            internal set
        var score: Long = -1
            internal set

        /** What this seat has put on the table so far. */
        val staked: Long get() = if (choice == Choice.CALL) ante * (1 + HoldemRules.CALL_ANTES) else ante
    }

    /**
     * One card on the table. [seat] is the player's seat, [DEALER] or [BOARD]; [index] its place there. [value] is
     * [Cards.HIDDEN] for the dealer's cards until [flipAt]. Times are ticks from the start of the deal.
     */
    data class ScriptCard(val seat: Int, val index: Int, val value: Int, val dealtAt: Int, val flipAt: Int = -1)

    /** A gesture of the dealer from [at]. [seat] and [index] say which card it deals or turns, or which seat it pays. */
    data class ScriptGesture(val gesture: Gesture, val at: Int, val seat: Int = 0, val index: Int = 0)

    /** What the table shows: cards, gestures, when it clears, and whom the dealer watches while players decide. */
    class Script {
        val cards = ArrayList<ScriptCard>()
        val gestures = ArrayList<ScriptGesture>()
        var clearAt = -1
        var watchFrom = -1
        var watchTo = -1
        var watchSeat = 1
    }

    /** The round is decided: what each seat gets back. Reported once, by the [tick] when the dealer turns his cards. */
    class Settled(val seats: List<Pair<Int, Seat>>)

    private val seats = arrayOfNulls<Seat>(SEATS)

    var phase: Phase = Phase.WAITING
        private set

    /** Ticks left before the cards (while betting) or before silence folds (while deciding); 0 otherwise. */
    var ticksLeft: Int = 0
        private set

    /** Ticks since the deal began; the script's clock. */
    var now: Int = 0
        private set

    var script = Script()
        private set
    var board: List<Int> = emptyList()
        private set
    var dealer: List<Int> = emptyList()
        private set
    private var deck: List<Int> = emptyList()
    private var dealerScore = -1L

    /** When the current phase ends, on the script's clock (dealing, showdown). */
    private var phaseEnd = 0
    private var resultAt = -1
    private var settled: Settled? = null

    fun seat(index: Int): Seat? = seats.getOrNull(index)

    fun seatOf(player: UUID): Int = seats.indexOfFirst { it?.player == player }

    val seated: List<Pair<Int, Seat>> get() = seats.mapIndexedNotNull { i, seat -> seat?.let { i to it } }

    /** The dealer's cards as the table shows them: face down until he turns them. */
    val dealerShown: List<Int> get() = dealer.mapIndexed { i, card -> if (revealed(i)) card else Cards.HIDDEN }

    /** The board cards dealt so far, on the script's clock. */
    val boardShown: List<Int> get() = script.cards.filter { it.seat == BOARD && it.dealtAt <= now }.sortedBy { it.index }.map { it.value }

    /** Whether the results are out: the dealer's cards are turned and every seat knows how it did. */
    val resultsOut: Boolean get() = resultAt >= 0 && now >= resultAt

    private fun revealed(index: Int): Boolean = script.cards.any { it.seat == DEALER && it.index == index && it.flipAt in 0..now }

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
        when (phase) {
            Phase.WAITING -> return null
            Phase.BETTING -> {
                if (--ticksLeft <= 0) deal()
                return null
            }
            Phase.DEALING -> {
                now++
                if (now >= phaseEnd) {
                    phase = Phase.DECISION
                    ticksLeft = DECISION_TICKS
                    script.watchFrom = now
                }
                return null
            }
            Phase.DECISION -> {
                now++
                val undecided = seated.firstOrNull { it.second.choice == Choice.NONE }
                if (undecided != null) script.watchSeat = undecided.first
                if (undecided != null && --ticksLeft > 0) return null
                ticksLeft = 0
                showdown()
                return null
            }
            Phase.SHOWDOWN -> {
                now++
                var out: Settled? = null
                if (now == resultAt) out = settled
                if (now >= phaseEnd) clear()
                return out
            }
        }
    }

    private fun deal() {
        deck = shuffled()
        script = Script()
        now = 0
        var next = 0
        fun draw() = deck[next++]
        val cards = HashMap<Int, MutableList<Int>>()
        var at = SHUFFLE_TICKS + FIRST_CARD_TICKS
        script.gestures += ScriptGesture(Gesture.SHUFFLE, 0)
        repeat(2) { round ->
            for ((index, _) in seated) {
                val card = draw()
                cards.getOrPut(index) { ArrayList() } += card
                script.cards += ScriptCard(index, round, card, at)
                script.gestures += ScriptGesture(Gesture.DEAL, at, index, round)
                at += CARD_TICKS
            }
            val card = draw()
            cards.getOrPut(DEALER) { ArrayList() } += card
            script.cards += ScriptCard(DEALER, round, card, at)
            script.gestures += ScriptGesture(Gesture.DEAL, at, DEALER, round)
            at += CARD_TICKS
        }
        val flop = (0 until 3).map { draw() }
        flop.forEachIndexed { i, card ->
            script.cards += ScriptCard(BOARD, i, card, at)
            script.gestures += ScriptGesture(Gesture.DEAL, at, BOARD, i)
            if (i < 2) at += CARD_TICKS
        }
        for ((index, seat) in seated) seat.cards = cards.getValue(index)
        dealer = cards.getValue(DEALER)
        board = flop
        deck = deck.subList(next, deck.size)
        phase = Phase.DEALING
        phaseEnd = at + DECISION_DELAY
    }

    private fun showdown() {
        script.watchTo = now
        val anyCall = seated.any { it.second.choice == Choice.CALL }
        for ((_, seat) in seated) if (seat.choice != Choice.CALL) seat.choice = Choice.FOLD
        var at = now
        if (anyCall) {
            // The turn and the river, then the dealer turns his second card and his first, as MineVegas's does.
            val last = deck.take(2)
            last.forEachIndexed { i, card ->
                script.cards += ScriptCard(BOARD, 3 + i, card, at)
                script.gestures += ScriptGesture(Gesture.DEAL, at, BOARD, 3 + i)
                at += CARD_TICKS
            }
            board = board + last
            at += REVEAL_DELAY - CARD_TICKS
            for (index in listOf(1, 0)) {
                flip(index, at)
                at += CARD_TICKS
            }
            at += RESOLVE_DELAY - CARD_TICKS
            dealerScore = Hands.score(dealer + board)
        } else {
            at += FOLD_DELAY
        }
        resultAt = at
        for ((_, seat) in seated) {
            if (seat.choice != Choice.CALL) {
                seat.result = HoldemRules.Result.FOLD
                continue
            }
            val score = Hands.score(seat.cards + board)
            val result = HoldemRules.result(score, dealerScore)
            seat.score = score
            seat.result = result
            seat.payout = HoldemRules.payout(seat.ante, result, score)
        }
        val gesture = reaction()
        script.gestures += ScriptGesture(gesture.first, at, gesture.second)
        val clearAt = at + RESULT_HOLD
        script.clearAt = clearAt
        script.gestures += ScriptGesture(Gesture.CLEAR, clearAt)
        settled = Settled(seated)
        phase = Phase.SHOWDOWN
        phaseEnd = clearAt + CLEAR_TICKS
        // Nothing to wait for when the round ends on the same tick.
        if (resultAt <= now) resultAt = now + 1
    }

    private fun flip(index: Int, at: Int) {
        val i = script.cards.indexOfFirst { it.seat == DEALER && it.index == index }
        if (i >= 0) script.cards[i] = script.cards[i].copy(flipAt = at)
        script.gestures += ScriptGesture(Gesture.FLIP, at, DEALER, index)
    }

    /** What the dealer does once the cards are turned: pays the best winner, pushes, or collects. */
    private fun reaction(): Pair<Gesture, Int> {
        val winners = seated.filter { it.second.result == HoldemRules.Result.WIN || it.second.result == HoldemRules.Result.NO_QUALIFY }
        if (winners.isNotEmpty()) {
            val best = winners.maxBy { it.second.payout }
            val big = HoldemRules.antePays(best.second.score) >= 3
            return (if (big) Gesture.CELEBRATE else Gesture.PAY) to best.first
        }
        seated.firstOrNull { it.second.result == HoldemRules.Result.PUSH }?.let { return Gesture.PUSH to it.first }
        return Gesture.COLLECT to (seated.firstOrNull()?.first ?: 1)
    }

    private fun clear() {
        seats.fill(null)
        board = emptyList()
        dealer = emptyList()
        deck = emptyList()
        script = Script()
        settled = null
        resultAt = -1
        dealerScore = -1
        now = 0
        phase = Phase.WAITING
        ticksLeft = 0
    }

    /** Stops a round that can't go on (the table is broken): every seat's stakes, to give back. The table is left empty. */
    fun abort(): List<Seat> {
        // A settled round has been paid already: nothing more is owed.
        val owed = if (phase == Phase.SHOWDOWN && resultsOut) emptyList() else seated.map { it.second }
        clear()
        return owed
    }

    companion object {
        const val SEATS = 3
        const val DEALER = -1
        const val BOARD = -2

        const val BETTING_TICKS = 200
        const val FULL_TABLE_TICKS = 40
        const val DECISION_TICKS = 500

        // The dealer's rhythm, in ticks, from MineVegas Casino's poker round.
        const val SHUFFLE_TICKS = 36
        const val FIRST_CARD_TICKS = 14
        const val CARD_TICKS = 22
        const val DECISION_DELAY = 27
        const val REVEAL_DELAY = 27
        const val RESOLVE_DELAY = 46
        const val FOLD_DELAY = 4
        const val RESULT_HOLD = 60
        const val CLEAR_TICKS = 30
    }
}
