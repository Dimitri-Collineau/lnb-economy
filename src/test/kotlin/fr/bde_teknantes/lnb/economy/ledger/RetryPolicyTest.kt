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
}
