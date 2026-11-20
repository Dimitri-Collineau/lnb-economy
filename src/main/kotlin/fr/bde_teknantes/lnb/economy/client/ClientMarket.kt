package fr.bde_teknantes.lnb.economy.client

import fr.bde_teknantes.lnb.economy.market.MarketPricing
import fr.bde_teknantes.lnb.economy.network.MarketCatalogItem
import fr.bde_teknantes.lnb.economy.network.MarketCatalogPayload
import fr.bde_teknantes.lnb.economy.network.MarketDeltaPayload
import fr.bde_teknantes.lnb.economy.network.MarketHistoryPayload
import fr.bde_teknantes.lnb.economy.network.MarketRequestPayload
import fr.bde_teknantes.lnb.economy.network.MarketResultPayload
import fr.bde_teknantes.lnb.economy.network.MarketWindowEntry
import fr.bde_teknantes.lnb.economy.network.MarketWindowPayload
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor

/**
 * The client's copy of the Market: what the server sent, and every price computed from it with [MarketPricing], the
 * same code the server uses. Nothing here calls the site, and no preview crosses the network.
 */
object ClientMarket {
    /** An item the game knows, with its curve. */
    class Entry(val data: MarketCatalogItem, val item: Item) {
        val id: String get() = data.id
        val stack = ItemStack(item)
        val name: String = stack.hoverName.string
        val curve = MarketPricing.Curve(data.p0, data.pMin, data.pMax, data.s0, data.sMin, data.sMax, data.k, data.alpha)
    }

    private class Stock(val value: Double, val atMillis: Long)

    var catalog: MarketCatalogPayload? = null
        private set
    var entries: List<Entry> = emptyList()
        private set
    var window: List<MarketWindowEntry> = emptyList()
        private set
    var capped = false
        private set

    /** Changes with every update, so a screen knows when to price again. */
    var revision = 0
        private set

    var toast: Pair<Boolean, Component>? = null
        private set
    var toastUntil = 0L
        private set

    private val byId = HashMap<String, Entry>()
    private val stocks = HashMap<String, Stock>()
    private val histories = HashMap<String, List<Float>>()
    private val quotes = HashMap<Triple<String, Int, Boolean>, Pair<Long, Long>>()

    val isOpen: Boolean get() = catalog?.open == true
    val sell: Boolean get() = catalog?.sell == true
    val buy: Boolean get() = catalog?.buy == true
    val saleAccount: Boolean get() = catalog?.saleAccount == true
    val maxOrderItems: Int get() = catalog?.maxOrderItems ?: 0
    val confirmAbove: Long get() = catalog?.confirmAbove ?: 0

    fun entry(id: String): Entry? = byId[id]

    fun update(payload: MarketCatalogPayload) {
        catalog = payload
        val now = System.currentTimeMillis()
        byId.clear()
        stocks.clear()
        val list = ArrayList<Entry>(payload.items.size)
        for (item in payload.items) {
            val found = ResourceLocation.tryParse(item.id)?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) } ?: continue
            val entry = Entry(item, found)
            list.add(entry)
            byId[item.id] = entry
            stocks[item.id] = Stock(item.stock, now)
        }
        entries = list.sortedBy { it.name.lowercase() }
        window = payload.window
        capped = payload.capped
        quotes.clear()
        revision++
    }

    fun update(payload: MarketDeltaPayload) {
        val now = System.currentTimeMillis()
        for ((id, stock) in payload.stocks) if (id in byId) stocks[id] = Stock(stock, now)
        quotes.clear()
        revision++
    }

    fun update(payload: MarketWindowPayload) {
        window = payload.window
        capped = payload.capped
        quotes.clear()
        revision++
    }

    fun update(payload: MarketHistoryPayload) {
        histories["${payload.item}|${payload.period}"] = payload.prices
        revision++
    }

    fun update(payload: MarketResultPayload) {
        toast = payload.ok to payload.message
        toastUntil = System.currentTimeMillis() + 6_000
        revision++
    }

    fun history(item: String, period: String): List<Float>? = histories["$item|$period"]

    /** Asks the server for a price history, once per call. */
    fun requestHistory(item: String, period: String) {
        PacketDistributor.sendToServer(MarketRequestPayload(MarketRequestPayload.HISTORY, item, period))
    }

    fun clear() {
        catalog = null
        entries = emptyList()
        window = emptyList()
        byId.clear()
        stocks.clear()
        histories.clear()
        quotes.clear()
        toast = null
        revision++
    }

    /** The stock now: the one the server sent, drifted since it arrived. */
    fun stockNow(entry: Entry): Double {
        val stock = stocks[entry.id] ?: return entry.data.stock
        return entry.curve.drift(stock.value, (System.currentTimeMillis() - stock.atMillis) / 3_600_000.0)
    }

    /** The price of one item with no spread, in LNBits. */
    fun reference(entry: Entry): Double = entry.curve.pRef(stockNow(entry))

    /** How far the price is from the base price: `(pRef - p0) / p0`. */
    fun change(entry: Entry): Double = (reference(entry) - entry.data.p0) / entry.data.p0

    /** The player's window in [tier], with the thresholds and rates the state gives. */
    fun window(tier: Int): MarketPricing.Window {
        val config = catalog?.tiers?.firstOrNull { it.tier == tier }
        val mine = window.firstOrNull { it.tier == tier }
        val q1 = mine?.q1 ?: ((config?.q1 ?: 0) / 100.0)
        val q2 = mine?.q2 ?: ((config?.q2 ?: 0) / 100.0)
        return MarketPricing.Window(mine?.nominal ?: 0.0, q1, q2, (config?.midPercent ?: 100.0) / 100.0, (config?.tailPercent ?: 100.0) / 100.0)
    }

    fun windowEntry(tier: Int): MarketWindowEntry? = window.firstOrNull { it.tier == tier }

    fun sellQuote(entry: Entry, quantity: Int): MarketPricing.Sale = MarketPricing.sell(
        entry.curve, catalog?.sigma ?: 0.0, stockNow(entry), quantity, window(entry.data.tier), catalog?.mintRemaining ?: Long.MAX_VALUE,
    )

    fun buyQuote(entry: Entry, quantity: Int): MarketPricing.Purchase =
        MarketPricing.buy(entry.curve, catalog?.sigma ?: 0.0, stockNow(entry), quantity)

    /**
     * The total in cents for [quantity] items, kept for a few seconds so a list of rows doesn't compute every frame.
     * Null when the item can't be sold (or bought) at all.
     */
    fun total(entry: Entry, quantity: Int, selling: Boolean): Long? {
        if (if (selling) !entry.data.sellable else !entry.data.buyable) return null
        val bucket = System.currentTimeMillis() / 5_000 + revision * 1_000_003L
        val key = Triple(entry.id, quantity, selling)
        quotes[key]?.takeIf { it.first == bucket }?.let { return it.second }
        val cents = if (selling) sellQuote(entry, quantity).totalCents else buyQuote(entry, quantity).totalCents
        quotes[key] = bucket to cents
        return cents
    }

    fun player(): Minecraft = Minecraft.getInstance()
}
