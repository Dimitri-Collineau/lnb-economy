package fr.bde_teknantes.lnb.economy.poker

import fr.bde_teknantes.lnb.economy.poker.table.BlackjackCardMotion
import fr.bde_teknantes.lnb.economy.poker.table.BlackjackLayout
import fr.bde_teknantes.lnb.economy.poker.table.DealerPlayAnimation
import fr.bde_teknantes.lnb.economy.poker.table.PokerLayout

/**
 * A round as a client draws it on the table: the [cards] and the dealer's gestures on the game clock, in the shapes
 * MineVegas Casino's card motion and dealer animation take (see `poker/table`). Built from what the server sends
 * with the block (see [HoldemRound.Script]); holds no rules.
 */
class PokerTableView private constructor(
    val cards: List<BlackjackCardMotion.Card>,
    private val gestures: List<Gesture>,
    private val watchFrom: Long,
    private val watchTo: Long,
    private val watchSeat: Int,
) {
    private class Gesture(val kind: DealerPlayAnimation.Kind, val at: Long, val seat: Int, val index: Int)

    /** Where [card] lies once dealt: a seat's place, the dealer's, or the board's. */
    fun slot(card: BlackjackCardMotion.Card): BlackjackLayout.Slot = PokerLayout.slot(card.toDealer, card.spot, card.index)

    /** The shuffle in progress at [now], and since when; null when the dealer isn't shuffling. */
    fun shuffleSince(now: Double): Double? = current(now)?.takeIf { it.kind == DealerPlayAnimation.Kind.SHUFFLE }?.let { now - it.at }

    /** The gesture the dealer is making at [now]: the latest one started and not over. */
    private fun current(now: Double): Gesture? =
        gestures.lastOrNull { it.at <= now }?.takeIf { now - it.at < DealerPlayAnimation.duration(it.kind) }

    /** What the dealer's arms and head do at [now], as MineVegas's poker table feeds its dealer. */
    fun dealerInput(now: Double, input: DealerPlayAnimation.Input) {
        input.clear()
        current(now)?.let { gesture ->
            input.kind = gesture.kind
            input.time = (now - gesture.at).toFloat()
            when (gesture.kind) {
                DealerPlayAnimation.Kind.DEAL -> {
                    val slot = slotOf(gesture.seat, gesture.index)
                    input.bearing = BlackjackLayout.bearing(slot.x(), slot.z()).toFloat()
                }
                DealerPlayAnimation.Kind.FLIP -> input.mirror = gesture.index == 0
                DealerPlayAnimation.Kind.PAY, DealerPlayAnimation.Kind.COLLECT, DealerPlayAnimation.Kind.PUSH -> {
                    val center = PokerLayout.seatCenter(gesture.seat)
                    input.bearing = BlackjackLayout.bearing(center[0], center[1]).toFloat()
                }
                else -> Unit
            }
        }
        if (watchFrom >= 0) {
            var w = smooth((now - watchFrom) / 10.0)
            if (watchTo >= 0) w *= 1.0 - smooth((now - watchTo) / 10.0)
            input.watch = w.toFloat()
            val center = PokerLayout.seatCenter(watchSeat)
            input.watchBearing = BlackjackLayout.bearing(center[0], center[1]).toFloat()
            input.watchTime = (now - watchFrom).toFloat()
        }
    }

    companion object {
        /** No round: an empty table and an idle dealer. */
        val EMPTY = PokerTableView(emptyList(), emptyList(), -1, -1, 1)

        private fun slotOf(seat: Int, index: Int): BlackjackLayout.Slot = when (seat) {
            HoldemRound.DEALER -> PokerLayout.dealerCard(index)
            HoldemRound.BOARD -> PokerLayout.dealerCard(2 + index)
            else -> PokerLayout.playerCard(seat, index)
        }

        private fun smooth(v: Double): Double {
            val x = v.coerceIn(0.0, 1.0)
            return x * x * (3.0 - 2.0 * x)
        }

        /** MineVegas's cards count ranks from the ace; [Cards] from the two. Suits are in the same order. */
        fun sheetValue(card: Int): Int = if (card !in 0 until Cards.COUNT) -1 else Cards.suit(card) * 13 + (Cards.rank(card) + 1) % 13

        /**
         * [base] is the game time of the script's tick 0. [cards] holds 5 ints a card (seat, index, value, dealt at,
         * flipped at), [gestures] 4 a gesture (kind, at, seat, index), [times] clear at, watch from, watch to, seat.
         */
        fun read(base: Long, cards: IntArray, gestures: IntArray, times: IntArray): PokerTableView {
            val clearAt = times.getOrElse(0) { -1 }
            fun at(tick: Int): Long = if (tick < 0) -1 else base + tick
            val list = (0 until cards.size / 5).map { i ->
                val o = i * 5
                val seat = cards[o]
                BlackjackCardMotion.Card().apply {
                    toDealer = seat < 0
                    index = if (seat == HoldemRound.BOARD) 2 + cards[o + 1] else cards[o + 1]
                    spot = seat.coerceIn(0, HoldemRound.SEATS - 1)
                    value = sheetValue(cards[o + 2])
                    // The dealer's two lie face down until he turns them; the board and the players' are face up.
                    faceUp = seat != HoldemRound.DEALER
                    dealtAt = base + cards[o + 3]
                    flipAt = at(cards[o + 4])
                    this.clearAt = at(clearAt)
                }
            }
            val kinds = HoldemRound.Gesture.entries
            val moves = (0 until gestures.size / 4).mapNotNull { i ->
                val o = i * 4
                val gesture = kinds.getOrNull(gestures[o]) ?: return@mapNotNull null
                Gesture(DealerPlayAnimation.Kind.valueOf(gesture.name), base + gestures[o + 1], gestures[o + 2], gestures[o + 3])
            }.sortedBy { it.at }
            return PokerTableView(list, moves, at(times.getOrElse(1) { -1 }), at(times.getOrElse(2) { -1 }), times.getOrElse(3) { 1 }.coerceIn(0, HoldemRound.SEATS - 1))
        }
    }
}
