package fr.bde_teknantes.lnb.economy.market

import java.time.Instant

/**
 * The server's copy of the Market: what `GET /state` said, kept current by the `market` events. Without Minecraft.
 * Server thread only. A copy older than the last revision seen is never used: see [apply] and [onStocks].
 */
class MarketState(private val clock: () -> Long = System::currentTimeMillis) {
    /** A stock as the site measured it, and when. */
    data class Stock(val stock: Double, val atMillis: Long)

    var view: MarketStateView? = null
        private set

    /** `economy.market` is off: `GET /state` answered 503 `disabled`. */
    var closed = false
        private set

    var revision = -1L
        private set

    private val stocks = HashMap<String, Stock>()
    private val items = HashMap<String, MarketItem>()

    val isOpen: Boolean get() = !closed && view != null

    val settings: MarketSettings? get() = view?.settings

    fun item(id: String): MarketItem? = items[id]

    val allItems: Collection<MarketItem> get() = items.values

    /** The state replaces everything. Items missing from it were turned off. */
    fun apply(state: MarketStateView) {
        view = state
        closed = false
        revision = state.revision
        items.clear()
        stocks.clear()
        for (item in state.items) {
            items[item.id] = item
            stocks[item.id] = Stock(item.stock, parse(item.stockAt))
        }
    }

    fun markClosed() {
        closed = true
        view = null
        revision = -1
        items.clear()
        stocks.clear()
    }

    /**
     * A `market` event: the stock right after orders. Returns true when the copy must be read again, because the
     * event's [revision] is ahead of ours (a configuration change went by unseen) or there is no copy.
     */
    fun onStocks(revision: Long, updates: List<MarketEventItem>): Boolean {
        if (view == null || revision > this.revision) return true
        for (update in updates) {
            if (update.id in items) stocks[update.id] = Stock(update.stock, parse(update.at))
        }
        return false
    }

    /** The stock an order's answer gives, measured now. Ignored for an item we don't list. */
    fun setStock(id: String, stock: Double) {
        if (id in items) stocks[id] = Stock(stock, clock())
    }

    /** `market-config`: [revision] is ahead of ours, so the state must be read again. */
    fun needsRefresh(revision: Long): Boolean = view == null || revision > this.revision

    /** The curve of [id], and its stock drifted to now. */
    fun curve(id: String): MarketPricing.Curve? = items[id]?.let {
        MarketPricing.Curve(it.p0, it.pMin, it.pMax, it.s0, it.sMin, it.sMax, it.k, it.alpha)
    }

    fun stockNow(id: String): Double? {
        val stock = stocks[id] ?: return null
        val curve = curve(id) ?: return null
        return curve.drift(stock.stock, (clock() - stock.atMillis) / 3_600_000.0)
    }

    /** A tier's thresholds in LNBits, from the state's cents, for a player with a [nominal] window. */
    fun window(tier: Int, nominal: Double): MarketPricing.Window? = view?.tiers?.firstOrNull { it.tier == tier }?.let {
        MarketPricing.Window(nominal, it.q1 / 100.0, it.q2 / 100.0, it.midPercent / 100.0, it.tailPercent / 100.0)
    }

    private fun parse(text: String): Long = runCatching { Instant.parse(text).toEpochMilli() }.getOrDefault(clock())
}
