package fr.bde_teknantes.lnb.economy.ledger

import fr.bde_teknantes.lnb.economy.api.Problems
import fr.bde_teknantes.lnb.warden.common.api.ApiResult

/** What a transfer's answer means for the journal. */
enum class Verdict {
    /** The money moved (or had already moved, `replayed`). */
    DONE,

    /** The site said no: nothing moved, and sending it again won't change that. */
    REFUSED,

    /** No answer, or a broken one: the money may or may not have moved. Send the same request again. */
    RETRY,

    /**
     * The site's answer proves a transaction exists under this key (`idempotencyMismatch` on a Market order), yet it
     * isn't the one we sent. Neither settle nor refuse: keep the entry and what it holds, stop retrying by itself,
     * and let an operator look (`/economy pending`, `/economy retry <key>`).
     */
    HOLD,
}

object RetryPolicy {
    /** Delays before each new attempt; the last one repeats until the site answers. */
    private val DELAYS_MS = longArrayOf(2_000, 5_000, 15_000, 60_000, 300_000)

    /** Market orders retry faster (1, 2, 4, 8 s, then every 30 s), as the site's spec says. */
    private val MARKET_DELAYS_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 30_000)

    /** Journal routes of Market orders. */
    const val MARKET_ORDERS = "/market/orders"

    /** Like [verdict], with what is specific to [route]. */
    fun verdict(result: ApiResult<*>, route: String): Verdict {
        if (route == MARKET_ORDERS && result is ApiResult.Http && result.problem == Problems.IDEMPOTENCY_MISMATCH) return Verdict.HOLD
        return verdict(result)
    }

    fun verdict(result: ApiResult<*>): Verdict = when (result) {
        is ApiResult.Ok -> Verdict.DONE
        is ApiResult.Network -> Verdict.RETRY
        is ApiResult.Http -> when {
            // A switched-off flag answers before anything moves.
            result.problem == Problems.DISABLED -> Verdict.REFUSED
            result.status == 429 || result.status >= 500 -> Verdict.RETRY
            // 401: the server's token is wrong. Nothing moved, but the transfer must not be lost: keep it for later.
            result.status == 401 -> Verdict.RETRY
            else -> Verdict.REFUSED
        }
    }

    /** How long to wait after the [attempts]-th failed attempt (1 for the first). */
    fun delayAfter(attempts: Int): Long = DELAYS_MS[(attempts - 1).coerceIn(0, DELAYS_MS.size - 1)]

    fun delayAfter(attempts: Int, route: String): Long {
        if (route != MARKET_ORDERS) return delayAfter(attempts)
        return MARKET_DELAYS_MS[(attempts - 1).coerceIn(0, MARKET_DELAYS_MS.size - 1)]
    }
}
