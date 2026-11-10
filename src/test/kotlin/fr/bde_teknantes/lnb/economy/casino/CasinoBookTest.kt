package fr.bde_teknantes.lnb.economy.casino

import kotlin.test.Test
import kotlin.test.assertEquals

class CasinoBookTest {
    private var now = 0L
    private val book = CasinoBook { now }

    @Test
    fun `free balance leaves out chips in circulation and reservations`() {
        book.update("royal", 10_000, frozen = false)
        book.reserve("table", "royal", 3_000, ttlMs = 60_000)
        book.reserve("other casino", "elsewhere", 5_000, ttlMs = 60_000)
        assertEquals(CasinoBook.Status.Open(10_000 - 2_500 - 3_000), book.status("royal", 2_500, maxAgeMs = 600_000))
    }

    @Test
    fun `a round checking itself again ignores its own reservation`() {
        book.update("royal", 1_000, frozen = false)
        book.reserve("table", "royal", 400, ttlMs = 60_000)
        assertEquals(CasinoBook.Status.Open(1_000), book.status("royal", 0, 600_000, except = "table"))
    }

    @Test
    fun `a new reservation for the same key replaces the old one, and release frees it`() {
        book.update("royal", 1_000, frozen = false)
        book.reserve("table", "royal", 400, ttlMs = 60_000)
        book.reserve("table", "royal", 100, ttlMs = 60_000)
        assertEquals(CasinoBook.Status.Open(900), book.status("royal", 0, 600_000))
        book.release("table")
        assertEquals(CasinoBook.Status.Open(1_000), book.status("royal", 0, 600_000))
    }

    @Test
    fun `reservations expire`() {
        book.update("royal", 1_000, frozen = false)
        book.reserve("table", "royal", 400, ttlMs = 60_000)
        now = 60_000
        book.updateBalance("royal", 1_000)
        assertEquals(CasinoBook.Status.Open(1_000), book.status("royal", 0, 600_000))
    }

    @Test
    fun `old balances, frozen and missing accounts can't be used`() {
        assertEquals(CasinoBook.Status.Unknown, book.status("royal", 0, 600_000))
        book.update("royal", 1_000, frozen = false)
        now = 600_001
        assertEquals(CasinoBook.Status.Unknown, book.status("royal", 0, 600_000))
        book.update("royal", 1_000, frozen = true)
        assertEquals(CasinoBook.Status.Frozen, book.status("royal", 0, 600_000))
        book.markMissing("royal")
        assertEquals(CasinoBook.Status.Missing, book.status("royal", 0, 600_000))
    }

    @Test
    fun `a free balance can be negative when chips outgrew the account`() {
        book.update("royal", 100, frozen = false)
        assertEquals(CasinoBook.Status.Open(-50), book.status("royal", 150, 600_000))
    }
}
