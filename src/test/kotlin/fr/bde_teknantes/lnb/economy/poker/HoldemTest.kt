package fr.bde_teknantes.lnb.economy.poker

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HoldemTest {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()
    private val c = UUID.randomUUID()

    /**
     * A deck that deals [text] first, in the dealer's order: one card to each seated player then one to the dealer,
     * twice; then the three board cards; then the turn and the river.
     */
    private fun round(text: String): HoldemRound {
        val first = Cards.parseAll(text)
        return HoldemRound { first + Cards.deck().filter { it !in first } }
    }

    /** Ticks until [phase], returning what got settled on the way. */
    private fun HoldemRound.until(phase: HoldemRound.Phase, limit: Int = 2000): HoldemRound.Settled? {
        var settled: HoldemRound.Settled? = null
        var left = limit
        while (this.phase != phase) {
            tick()?.let { settled = it }
            check(--left > 0) { "never reached $phase" }
        }
        return settled
    }

    private fun HoldemRound.ticks(count: Int): HoldemRound.Settled? {
        var settled: HoldemRound.Settled? = null
        repeat(count) { tick()?.let { settled = it } }
        return settled
    }

    private fun score(text: String) = Hands.score(Cards.parseAll(text))

    @Test
    fun `the dealer plays with a pair of fours or better`() {
        assertFalse(HoldemRules.qualifies(score("As Kd 9h 7c 5s")))
        assertFalse(HoldemRules.qualifies(score("3s 3d Ah Kc Qs")))
        assertTrue(HoldemRules.qualifies(score("4s 4d 2h 3c 6s")))
        assertTrue(HoldemRules.qualifies(score("2s 2d 3h 3c 6s")))
    }

    @Test
    fun `pays the ante from the table and the call one to one`() {
        val pair = score("As Ad 9h 7c 5s")
        assertEquals(0, HoldemRules.payout(10, HoldemRules.Result.FOLD, pair))
        assertEquals(0, HoldemRules.payout(10, HoldemRules.Result.LOSE, pair))
        assertEquals(30, HoldemRules.payout(10, HoldemRules.Result.PUSH, pair))
        assertEquals(40, HoldemRules.payout(10, HoldemRules.Result.NO_QUALIFY, pair))
        assertEquals(60, HoldemRules.payout(10, HoldemRules.Result.WIN, pair))
        assertEquals(80, HoldemRules.payout(10, HoldemRules.Result.WIN, score("As Ad Ah 7c 7s")))
        assertEquals(10L * HoldemRules.MAX_RETURN_ANTES, HoldemRules.payout(10, HoldemRules.Result.WIN, score("Ts Js Qs Ks As")))
    }

    @Test
    fun `three players play one round against the same dealer and board`() {
        // Seats 0, 1, 2 then the dealer, twice. A: aces. B: 8 4. C: kings. Dealer: queens. Board: 2 7 9, then J 3.
        val round = round("As 8c Ks Qs  Ad 4h Kd Qd  2h 7c 9s  Jh 3d")
        assertTrue(round.sit(0, a, "A", 10))
        assertEquals(HoldemRound.Phase.BETTING, round.phase)
        assertTrue(round.sit(2, c, "C", 50))
        assertTrue(round.sit(1, b, "B", 20))
        assertFalse(round.sit(1, UUID.randomUUID(), "D", 5), "a taken seat stays taken")

        round.ticks(HoldemRound.FULL_TABLE_TICKS)
        assertEquals(HoldemRound.Phase.DEALING, round.phase)
        assertFalse(round.call(a), "nobody decides while the cards are dealt")
        assertFalse(round.sit(0, UUID.randomUUID(), "late", 5), "nobody joins once the cards are out")
        // Shuffle, then 8 hole cards and 3 board cards, one every CARD_TICKS.
        assertEquals(11, round.script.cards.size)
        assertEquals(HoldemRound.Gesture.SHUFFLE, round.script.gestures.first().gesture)
        val dealtAt = round.script.cards.map { it.dealtAt }
        assertEquals(dealtAt.sorted(), dealtAt)
        assertTrue(dealtAt.zipWithNext().all { (x, y) -> y - x == HoldemRound.CARD_TICKS })

        assertNull(round.until(HoldemRound.Phase.DECISION))
        assertEquals(Cards.parseAll("As Ad"), round.seat(0)!!.cards)
        assertEquals(Cards.parseAll("8c 4h"), round.seat(1)!!.cards)
        assertEquals(Cards.parseAll("Ks Kd"), round.seat(2)!!.cards)
        assertEquals(listOf(Cards.HIDDEN, Cards.HIDDEN), round.dealerShown)
        assertEquals(3, round.boardShown.size)

        assertTrue(round.call(a))
        assertFalse(round.call(a), "one decision each")
        assertTrue(round.fold(b))
        assertTrue(round.call(c))
        round.ticks(1)
        assertEquals(HoldemRound.Phase.SHOWDOWN, round.phase, "the cards come as soon as everyone has chosen")
        assertFalse(round.resultsOut)

        val settled = assertNotNull(round.until(HoldemRound.Phase.WAITING), "the round settles when the dealer turns his cards")
        val outcome = settled.seats.associate { (index, seat) -> index to (seat.result to seat.payout) }
        assertEquals(HoldemRules.Result.WIN to 60L, outcome[0])
        assertEquals(HoldemRules.Result.FOLD to 0L, outcome[1])
        assertEquals(HoldemRules.Result.WIN to 300L, outcome[2])
        assertTrue(round.seated.isEmpty())
        assertTrue(round.canSit(0, a))
    }

    @Test
    fun `the dealer turns his cards before anyone is paid`() {
        val round = round("As Qs Ad Qd  2h 7c 9s  Jh 3d")
        round.sit(1, a, "A", 10)
        round.until(HoldemRound.Phase.DECISION)
        round.call(a)
        round.ticks(1)
        var settledAt = -1
        var shownAt = -1
        while (round.phase == HoldemRound.Phase.SHOWDOWN) {
            if (shownAt < 0 && Cards.HIDDEN !in round.dealerShown) shownAt = round.now
            if (round.tick() != null) settledAt = round.now
        }
        assertTrue(shownAt in 1..settledAt, "turned at $shownAt, settled at $settledAt")
    }

    @Test
    fun `a dealer who doesn't qualify pays the ante and gives the call back`() {
        // A: As Kd. Dealer: 3c 2h. Board: 5h 7c 9s, then Jh Qd: nothing pairs.
        val round = round("As 3c Kd 2h  5h 7c 9s  Jh Qd")
        round.sit(0, a, "A", 10)
        round.until(HoldemRound.Phase.DECISION)
        round.call(a)
        val seat = assertNotNull(round.until(HoldemRound.Phase.WAITING)).seats.single().second
        assertEquals(HoldemRules.Result.NO_QUALIFY, seat.result)
        assertEquals(40, seat.payout)
    }

    @Test
    fun `silence folds when the time runs out, and nothing more is dealt`() {
        val round = round("As Qs Ad Qd  2h 7c 9s  Jh 3d")
        round.sit(1, a, "A", 10)
        round.until(HoldemRound.Phase.DECISION)
        round.ticks(HoldemRound.DECISION_TICKS - 1)
        assertEquals(HoldemRound.Phase.DECISION, round.phase)
        val seat = assertNotNull(round.until(HoldemRound.Phase.WAITING)).seats.single().second
        assertEquals(HoldemRules.Result.FOLD, seat.result)
        assertEquals(0, seat.payout)
        assertEquals(10, seat.staked)
    }

    @Test
    fun `aborting gives the stakes back, once`() {
        val round = round("As Ks Qs Ad Kd Qd  2h 7c 9s  Jh 3d")
        round.sit(0, a, "A", 10)
        round.sit(1, b, "B", 20)
        round.until(HoldemRound.Phase.DECISION)
        round.call(a)
        assertEquals(listOf(30L, 20L), round.abort().map { it.staked })
        assertEquals(HoldemRound.Phase.WAITING, round.phase)
        assertTrue(round.abort().isEmpty())

        round.sit(0, a, "A", 10)
        round.until(HoldemRound.Phase.DECISION)
        round.call(a)
        round.ticks(1)
        assertEquals(listOf(30L), round.abort().map { it.staked }, "before the cards are turned, nothing was paid yet")

        round.sit(0, a, "A", 10)
        round.until(HoldemRound.Phase.DECISION)
        round.call(a)
        while (!round.resultsOut) round.tick()
        assertTrue(round.abort().isEmpty(), "a settled round was paid already")
    }

    @Test
    fun `one player can't take two seats`() {
        val round = round("As Qs Ad Qd  2h 7c 9s  Jh 3d")
        assertTrue(round.sit(0, a, "A", 10))
        assertFalse(round.sit(1, a, "A", 10))
        assertFalse(round.sit(2, b, "B", 0))
    }
}
