package fr.bde_teknantes.lnb.economy.market

/*
 * The bodies and answers of the `/api/economy/server/market` routes (`market-mod.md` on the site, section 4).
 * Units differ on purpose: totals and `mintRemaining` are integer cents, `/state` thresholds are cents of nominal
 * value, a player's window is LNBits, and per-item prices are LNBits as `Double`.
 */

data class MarketSettings(
    val sigma: Double,
    val tolerancePercent: Double,
    val maxOrderItems: Int,
    val sell: Boolean,
    val buy: Boolean,
    /** The site lets a sale pay a chosen account. Absent on older sites, so false. */
    val saleAccount: Boolean = false,
)

/** `q1` and `q2` in cents of nominal value, rates in percent. */
data class MarketTier(val tier: Int, val q1: Long, val q2: Long, val midPercent: Double, val tailPercent: Double)

data class MarketItem(
    val id: String,
    val tier: Int,
    val sellable: Boolean,
    val buyable: Boolean,
    val p0: Double,
    val pMin: Double,
    val pMax: Double,
    val s0: Double,
    val sMin: Double,
    val sMax: Double,
    val k: Double,
    val alpha: Double,
    val stock: Double,
    /** ISO 8601: when [stock] was measured. */
    val stockAt: String,
)

/** `GET /state`. Only enabled items are listed. */
data class MarketStateView(
    val revision: Long,
    val asOf: String,
    val settings: MarketSettings,
    val tiers: List<MarketTier>,
    /** Cents of mint budget left over the next 30 days. */
    val mintRemaining: Long,
    val items: List<MarketItem>,
)

/** A player's window in one tier. All amounts are LNBits, rates are fractions. */
data class MarketWindowTier(
    val tier: Int,
    val nominal: Double,
    val rate: Double,
    val toNextBracket: Double?,
    val q1: Double,
    val q2: Double,
    val nextRate: Double?,
)

/** `GET /players/:uuid`. */
data class MarketPlayerWindow(val tiers: List<MarketWindowTier>, val capped: Boolean)

/** `POST /quote`. */
data class MarketQuoteBody(val player: String, val item: String, val direction: String, val quantity: Int)

data class MarketBracketView(val rate: Double, val quantity: Double, val total: Long)

/** A quote, and the `quote` of a `priceChanged` refusal. [total] is cents; [price] LNBits per item after the order. */
data class MarketQuote(
    val direction: String,
    val item: String,
    val quantity: Int,
    val total: Long,
    val capped: Boolean,
    val brackets: List<MarketBracketView>? = null,
    val price: Double,
    val stock: Double,
)

/** `POST /orders`. [account] is a purchase's paying account (ID or `#handle`), null for the default one. */
data class MarketOrderBody(
    val idempotencyKey: String,
    val player: String,
    val item: String,
    val direction: String,
    val quantity: Int,
    val expectedTotal: Long,
    val account: String? = null,
) {
    companion object {
        const val SELL = "sell"
        const val BUY = "buy"
    }
}

data class MarketOrderAccount(val accountId: String, val handle: String?, val balance: Long)

data class MarketOrderResult(
    val direction: String,
    val item: String,
    val quantity: Int,
    val total: Long,
    val capped: Boolean,
    val brackets: List<MarketBracketView>? = null,
    val price: Double,
    val stock: Double,
    val transactionId: Long,
    val replayed: Boolean,
    val account: MarketOrderAccount,
    /** A purchase's item, to claim at once. */
    val mailboxItemId: String? = null,
)

data class RecipeStack(val id: String, val count: Int)

data class MarketRecipe(val type: String, val output: RecipeStack, val inputs: List<RecipeStack>)

data class MarketRecipeBody(val recipes: List<MarketRecipe>)

data class RecipeProblem(val item: String? = null, val value: Double? = null, val cost: Double? = null)

data class MarketRecipeResult(val stored: Int, val changed: Boolean, val problems: List<RecipeProblem> = emptyList())

/** `market` event: the stock right after orders, not drifted. */
data class MarketEventItem(val id: String, val stock: Double, val at: String)

/** `GET /api/market/items/:id/history`: public, one point per snapshot or bucket. */
data class MarketHistoryPoint(val at: String, val price: Double)

data class MarketHistoryView(val period: String, val points: List<MarketHistoryPoint> = emptyList())
