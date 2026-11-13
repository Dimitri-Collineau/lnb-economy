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

    /** A deck that deals [text] first: two cards per seat in seat order, the dealer's two, then the five board cards. */
    private fun round(text: String): HoldemRound {
        val first = Cards.parseAll(text)
        return HoldemRound { first + Cards.deck().filter { it !in first } }
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
        // A: aces. B: nothing. C: kings. Dealer: queens. Board: 2 7 9 J 3, no flush, no straight.
        val round = round("As Ad  8c 4h  Ks Kd  Qs Qd  2h 7c 9s Jh 3d")
        assertTrue(round.sit(0, a, "A", 10))
        assertEquals(HoldemRound.Phase.BETTING, round.phase)
        assertTrue(round.sit(2, c, "C", 50))
        assertTrue(round.sit(1, b, "B", 20))
        assertFalse(round.sit(1, UUID.randomUUID(), "D", 5), "a taken seat stays taken")

        assertNull(round.ticks(HoldemRound.FULL_TABLE_TICKS))
        assertEquals(HoldemRound.Phase.DECISION, round.phase)
        assertEquals(Cards.parseAll("As Ad"), round.seat(0)!!.cards)
        assertEquals(Cards.parseAll("8c 4h"), round.seat(1)!!.cards)
        assertEquals(Cards.parseAll("Ks Kd"), round.seat(2)!!.cards)
        assertEquals(listOf(Cards.HIDDEN, Cards.HIDDEN), round.dealerShown)
        assertEquals(3, round.board.size)
        assertFalse(round.sit(0, UUID.randomUUID(), "late", 5), "nobody joins once the cards are out")

        assertTrue(round.call(a))
        assertFalse(round.call(a), "one decision each")
        assertTrue(round.fold(b))
        assertTrue(round.call(c))
        val settled = assertNotNull(round.ticks(1), "the round settles as soon as everyone has chosen")
        assertEquals(HoldemRound.Phase.SHOWDOWN, round.phase)
        assertEquals(Cards.parseAll("Qs Qd"), round.dealerShown)
        assertEquals(5, round.board.size)

        val outcome = settled.seats.associate { (index, seat) -> index to (seat.result to seat.payout) }
        assertEquals(HoldemRules.Result.WIN to 60L, outcome[0])
        assertEquals(HoldemRules.Result.FOLD to 0L, outcome[1])
        assertEquals(HoldemRules.Result.WIN to 300L, outcome[2])

        assertNull(round.ticks(HoldemRound.SHOWDOWN_TICKS))
        assertEquals(HoldemRound.Phase.WAITING, round.phase)
        assertTrue(round.seated.isEmpty())
        assertTrue(round.canSit(0, a))
    }

    @Test
    fun `a dealer who doesn't qualify pays the ante and gives the call back`() {
        // Dealer: 3 and 2 with a board that pairs nothing.
        val round = round("As Kd  3c 2h  5h 7c 9s Jh Qd")
        round.sit(0, a, "A", 10)
        round.ticks(HoldemRound.BETTING_TICKS)
        round.call(a)
        val seat = assertNotNull(round.ticks(1)).seats.single().second
        assertEquals(HoldemRules.Result.NO_QUALIFY, seat.result)
        assertEquals(40, seat.payout)
    }

    @Test
    fun `silence folds when the time runs out`() {
        val round = round("As Ad  Qs Qd  2h 7c 9s Jh 3d")
        round.sit(1, a, "A", 10)
        round.ticks(HoldemRound.BETTING_TICKS)
        assertNull(round.ticks(HoldemRound.DECISION_TICKS - 1))
        val seat = assertNotNull(round.ticks(1)).seats.single().second
        assertEquals(HoldemRules.Result.FOLD, seat.result)
        assertEquals(0, seat.payout)
        assertEquals(10, seat.staked)
    }

    @Test
    fun `aborting gives the stakes back, once`() {
        val round = round("As Ad  Ks Kd  Qs Qd  2h 7c 9s Jh 3d")
        round.sit(0, a, "A", 10)
        round.sit(1, b, "B", 20)
        round.ticks(HoldemRound.BETTING_TICKS)
        round.call(a)
        assertEquals(listOf(30L, 20L), round.abort().map { it.staked })
        assertEquals(HoldemRound.Phase.WAITING, round.phase)
        assertTrue(round.abort().isEmpty())

        round.sit(0, a, "A", 10)
        round.ticks(HoldemRound.BETTING_TICKS)
        round.call(a)
        round.ticks(1)
        assertTrue(round.abort().isEmpty(), "a settled round was paid already")
    }

    @Test
    fun `one player can't take two seats`() {
        val round = round("As Ad  Qs Qd  2h 7c 9s Jh 3d")
        assertTrue(round.sit(0, a, "A", 10))
        assertFalse(round.sit(1, a, "A", 10))
        assertFalse(round.sit(2, b, "B", 0))
    }
}
