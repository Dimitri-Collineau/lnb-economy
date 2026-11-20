package fr.bde_teknantes.lnb.economy.ledger

import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RetryPolicyTest {
    @Test
    fun `answers that guarantee nothing moved are final`() {
        assertEquals(Verdict.REFUSED, RetryPolicy.verdict(ApiResult.Http(409, "insufficientFunds")))
        assertEquals(Verdict.REFUSED, RetryPolicy.verdict(ApiResult.Http(404, "notLinked")))
        assertEquals(Verdict.REFUSED, RetryPolicy.verdict(ApiResult.Http(503, "disabled")))
    }

    @Test
    fun `answers that may hide a move are retried`() {
        assertEquals(Verdict.RETRY, RetryPolicy.verdict(ApiResult.Network(RuntimeException())))
        assertEquals(Verdict.RETRY, RetryPolicy.verdict(ApiResult.Http(500, null)))
        assertEquals(Verdict.RETRY, RetryPolicy.verdict(ApiResult.Http(503, null)))
        assertEquals(Verdict.RETRY, RetryPolicy.verdict(ApiResult.Http(429, null)))
        assertEquals(Verdict.RETRY, RetryPolicy.verdict(ApiResult.Http(401, null)))
    }

    @Test
    fun `delays grow and then stay at the last one`() {
        val delays = (1..8).map(RetryPolicy::delayAfter)
        assertTrue(delays.zipWithNext().all { (a, b) -> a <= b })
        assertEquals(delays[5], delays[7])
    }

    @Test
    fun `a market order retries after 1, 2, 4, 8 then 30 seconds`() {
        val delays = (1..7).map { RetryPolicy.delayAfter(it, RetryPolicy.MARKET_ORDERS) }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 30_000L, 30_000L, 30_000L), delays)
    }

    @Test
    fun `other routes keep the usual delays`() {
        assertEquals(RetryPolicy.delayAfter(3), RetryPolicy.delayAfter(3, "/auctions"))
    }

    @Test
    fun `a key reused for another market order is held, not refused or retried`() {
        val mismatch = ApiResult.Http(409, "idempotencyMismatch")
        assertEquals(Verdict.HOLD, RetryPolicy.verdict(mismatch, RetryPolicy.MARKET_ORDERS))
        assertEquals(Verdict.REFUSED, RetryPolicy.verdict(mismatch, "/auctions"))
    }

    @Test
    fun `a market order refused by the market being closed is final`() {
        assertEquals(Verdict.REFUSED, RetryPolicy.verdict(ApiResult.Http(503, "disabled"), RetryPolicy.MARKET_ORDERS))
    }
}
