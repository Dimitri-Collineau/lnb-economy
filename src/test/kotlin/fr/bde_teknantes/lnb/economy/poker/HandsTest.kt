package fr.bde_teknantes.lnb.economy.poker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandsTest {
    private fun score(text: String) = Hands.score(Cards.parseAll(text))

    private fun category(text: String) = Hands.category(score(text))

    @Test
    fun `names every category from seven cards`() {
        assertEquals(Hands.HIGH_CARD, category("As Kd 9h 7c 5s 3d 2h"))
        assertEquals(Hands.PAIR, category("As Ad 9h 7c 5s 3d 2h"))
        assertEquals(Hands.TWO_PAIR, category("As Ad 9h 9c 5s 3d 2h"))
        assertEquals(Hands.TRIPS, category("As Ad Ah 9c 5s 3d 2h"))
        assertEquals(Hands.STRAIGHT, category("5s 6d 7h 8c 9s Kd 2h"))
        assertEquals(Hands.FLUSH, category("As Ks 9s 7s 5s 3d 2h"))
        assertEquals(Hands.FULL_HOUSE, category("As Ad Ah 9c 9s 3d 2h"))
        assertEquals(Hands.QUADS, category("As Ad Ah Ac 9s 3d 2h"))
        assertEquals(Hands.STRAIGHT_FLUSH, category("5s 6s 7s 8s 9s Kd 2h"))
    }

    @Test
    fun `higher categories beat lower ones`() {
        val ordered = listOf(
            "As Kd 9h 7c 5s", "2s 2d 9h 7c 5s", "3s 3d 2h 2c 5s", "2s 2d 2h 7c 5s", "As 2d 3h 4c 5s",
            "2s 4s 6s 8s Ts", "2s 2d 2h 3c 3s", "2s 2d 2h 2c 3s", "As 2s 3s 4s 5s",
        ).map(::score)
        assertEquals(ordered.sorted(), ordered)
        assertEquals(ordered.size, ordered.toSet().size)
    }

    @Test
    fun `the wheel is the lowest straight and the ace plays high elsewhere`() {
        assertTrue(score("As 2d 3h 4c 5s") < score("2s 3d 4h 5c 6s"))
        assertTrue(score("Ts Jd Qh Kc As") > score("9s Td Jh Qc Ks"))
        assertEquals(Hands.HIGH_CARD, category("Qs Kd Ah 2c 3s"))
    }

    @Test
    fun `kickers break ties and suits never do`() {
        assertTrue(score("As Ad Kh 7c 5s") > score("Ah Ac Qh 7d 5c"))
        assertTrue(score("As Ad 9h 9c Ks") > score("Ah Ac 9s 9d Qs"))
        assertEquals(score("As Kd 9h 7c 5s"), score("Ah Kc 9s 7d 5h"))
    }

    @Test
    fun `uses the best five of seven`() {
        // Two trips make a full house, three pairs keep the best two and the best kicker.
        assertEquals(score("Ks Kd Kh Qc Qs"), score("Ks Kd Kh Qc Qs Qd 2h"))
        assertEquals(score("As Ad Kh Kc Qs"), score("As Ad Kh Kc Qs Qd 2h"))
        // A flush beats the straight lying in the same cards; six suited cards keep the top five.
        assertEquals(Hands.FLUSH, category("5s 6s 7s 8s 9d Ks 2h"))
        assertEquals(score("As Ks Qs 9s 7s"), score("As Ks Qs 9s 7s 2s 3d"))
        // A straight flush must be in one suit.
        assertEquals(Hands.FLUSH, category("5s 6s 7s 8s 9d As 2h"))
    }

    @Test
    fun `only the ace-high straight flush is royal`() {
        assertTrue(Hands.isRoyal(score("Ts Js Qs Ks As 2d 3h")))
        assertFalse(Hands.isRoyal(score("9s Ts Js Qs Ks 2d 3h")))
        assertFalse(Hands.isRoyal(score("Ts Jd Qh Kc As 2d 3h")))
    }
}
