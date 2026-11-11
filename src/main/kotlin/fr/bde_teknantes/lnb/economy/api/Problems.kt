package fr.bde_teknantes.lnb.economy.api

import fr.bde_teknantes.lnb.warden.common.api.ApiResult

/** How the mod reads the site's `data.problem` codes, listed in `docs/economy.md`. */
object Problems {
    const val INSUFFICIENT_FUNDS = "insufficientFunds"
    const val FROZEN = "frozen"
    const val NOT_ALLOWED = "notAllowed"
    const val NOT_LINKED = "notLinked"
    const val NOT_ELIGIBLE = "notEligible"
    const val NOT_FOUND = "notFound"
    const val CLOSED = "closed"
    const val SAME_ACCOUNT = "sameAccount"
    const val IDEMPOTENCY_MISMATCH = "idempotencyMismatch"
    const val DISABLED = "disabled"
    const val SHOP_DISABLED = "shopDisabled"
    const val PRICE_CHANGED = "priceChanged"
    const val NO_BUY_BACK = "noBuyBack"

    /** Problems with their own message; anything else gets the generic one with its code. */
    val KNOWN = setOf(
        INSUFFICIENT_FUNDS, FROZEN, NOT_ALLOWED, NOT_LINKED, NOT_ELIGIBLE, NOT_FOUND, CLOSED, SAME_ACCOUNT, DISABLED,
        SHOP_DISABLED, PRICE_CHANGED, NO_BUY_BACK,
    )

    /** Players without an economy: no linked LNB account, or an LNB account without access. */
    fun isNoEconomy(problem: String?): Boolean = problem == NOT_LINKED || problem == NOT_ELIGIBLE

    /**
     * The translation key of the message shown to a player for a failed call.
     * Network errors and 5xx answers read as "the economy is unavailable".
     */
    fun messageKey(result: ApiResult<*>): String = when {
        result is ApiResult.Http && result.problem == DISABLED -> "economy.lnbeconomy.problem.disabled"
        result.isUnreachable -> "economy.lnbeconomy.problem.unavailable"
        result is ApiResult.Http && result.problem in KNOWN -> "economy.lnbeconomy.problem.${result.problem}"
        else -> "economy.lnbeconomy.problem.other"
    }

    /** The code shown with the generic message. */
    fun code(result: ApiResult<*>): String = when (result) {
        is ApiResult.Http -> result.problem ?: "HTTP ${result.status}"
        else -> result.describe()
    }
}
