package fr.bde_teknantes.lnb.economy.network

import fr.bde_teknantes.lnb.economy.LnbEconomy
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/*
 * The Market's packets. The server relays everything: the client never calls the site. The client prices orders itself
 * from the catalog (`MarketPricing`), so no preview ever crosses the network.
 */

/** An item of the catalog, with its curve. [stock] is already drifted to the moment the server sent it. */
data class MarketCatalogItem(
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
) {
    companion object {
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MarketCatalogItem> = StreamCodec.of(
            { buf, i ->
                buf.writeUtf(i.id, 128)
                buf.writeVarInt(i.tier)
                buf.writeBoolean(i.sellable)
                buf.writeBoolean(i.buyable)
                for (d in listOf(i.p0, i.pMin, i.pMax, i.s0, i.sMin, i.sMax, i.k, i.alpha, i.stock)) buf.writeDouble(d)
            },
            { buf ->
                MarketCatalogItem(
                    buf.readUtf(128), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                )
            },
        )
    }
}

/** A tier's thresholds in cents of nominal value and its rates in percent, as the state gives them. */
data class MarketCatalogTier(val tier: Int, val q1: Long, val q2: Long, val midPercent: Double, val tailPercent: Double) {
    companion object {
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MarketCatalogTier> = StreamCodec.of(
            { buf, t ->
                buf.writeVarInt(t.tier); buf.writeVarLong(t.q1); buf.writeVarLong(t.q2)
                buf.writeDouble(t.midPercent); buf.writeDouble(t.tailPercent)
            },
            { buf -> MarketCatalogTier(buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readDouble(), buf.readDouble()) },
        )
    }
}

/** A player's window in one tier (LNBits, rates as fractions). */
data class MarketWindowEntry(
    val tier: Int,
    val nominal: Double,
    val rate: Double,
    val toNextBracket: Double?,
    val q1: Double,
    val q2: Double,
    val nextRate: Double?,
) {
    companion object {
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MarketWindowEntry> = StreamCodec.of(
            { buf, w ->
                buf.writeVarInt(w.tier); buf.writeDouble(w.nominal); buf.writeDouble(w.rate)
                buf.writeNullable(w.toNextBracket) { b, v -> b.writeDouble(v) }
                buf.writeDouble(w.q1); buf.writeDouble(w.q2)
                buf.writeNullable(w.nextRate) { b, v -> b.writeDouble(v) }
            },
            { buf ->
                MarketWindowEntry(
                    buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readNullable { it.readDouble() },
                    buf.readDouble(), buf.readDouble(), buf.readNullable { it.readDouble() },
                )
            },
        )
    }
}

/**
 * Server -> client: everything the screen needs to price an order. [open] false means the Market is closed (the other
 * fields are then empty). Sent when the screen opens and whenever the site's configuration changes.
 */
data class MarketCatalogPayload(
    val open: Boolean,
    val revision: Long,
    val sell: Boolean,
    val buy: Boolean,
    val saleAccount: Boolean,
    val sigma: Double,
    val maxOrderItems: Int,
    val mintRemaining: Long,
    val tiers: List<MarketCatalogTier>,
    val items: List<MarketCatalogItem>,
    val window: List<MarketWindowEntry>,
    val capped: Boolean,
    /** Orders above this many cents ask for a second click. */
    val confirmAbove: Long = 0,
    /** The answer to opening the screen: the client opens it. Later catalogs only refresh a screen already open. */
    val fresh: Boolean = false,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<MarketCatalogPayload>(LnbEconomy.id("market_catalog"))

        fun closed(fresh: Boolean = false) = MarketCatalogPayload(false, -1, false, false, false, 0.0, 0, 0, emptyList(), emptyList(), emptyList(), false, 0, fresh)

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MarketCatalogPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeBoolean(p.open); buf.writeVarLong(p.revision)
                buf.writeBoolean(p.sell); buf.writeBoolean(p.buy); buf.writeBoolean(p.saleAccount)
                buf.writeDouble(p.sigma); buf.writeVarInt(p.maxOrderItems); buf.writeVarLong(p.mintRemaining)
                buf.writeVarInt(p.tiers.size); p.tiers.forEach { MarketCatalogTier.STREAM_CODEC.encode(buf, it) }
                buf.writeVarInt(p.items.size); p.items.forEach { MarketCatalogItem.STREAM_CODEC.encode(buf, it) }
                buf.writeVarInt(p.window.size); p.window.forEach { MarketWindowEntry.STREAM_CODEC.encode(buf, it) }
                buf.writeBoolean(p.capped); buf.writeVarLong(p.confirmAbove); buf.writeBoolean(p.fresh)
            },
            { buf ->
                MarketCatalogPayload(
                    buf.readBoolean(), buf.readVarLong(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readDouble(), buf.readVarInt(), buf.readVarLong(),
                    List(buf.readVarInt()) { MarketCatalogTier.STREAM_CODEC.decode(buf) },
                    List(buf.readVarInt()) { MarketCatalogItem.STREAM_CODEC.decode(buf) },
                    List(buf.readVarInt()) { MarketWindowEntry.STREAM_CODEC.decode(buf) },
                    buf.readBoolean(), buf.readVarLong(), buf.readBoolean(),
                )
            },
        )
    }
}

/** Server -> client: new stocks (already drifted to now) after orders, and the new mint budget. */
data class MarketDeltaPayload(val revision: Long, val mintRemaining: Long, val stocks: Map<String, Double>) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<MarketDeltaPayload>(LnbEconomy.id("market_delta"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MarketDeltaPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeVarLong(p.revision); buf.writeVarLong(p.mintRemaining)
                buf.writeVarInt(p.stocks.size)
                p.stocks.forEach { (id, stock) -> buf.writeUtf(id, 128); buf.writeDouble(stock) }
            },
            { buf ->
                val revision = buf.readVarLong()
                val mint = buf.readVarLong()
                val stocks = LinkedHashMap<String, Double>()
                repeat(buf.readVarInt()) { stocks[buf.readUtf(128)] = buf.readDouble() }
                MarketDeltaPayload(revision, mint, stocks)
            },
        )
    }
}

/** Server -> client: the player's windows, after one of their orders or when asked. */
data class MarketWindowPayload(val window: List<MarketWindowEntry>, val capped: Boolean) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<MarketWindowPayload>(LnbEconomy.id("market_window"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MarketWindowPayload> = StreamCodec.of(
            { buf, p -> buf.writeVarInt(p.window.size); p.window.forEach { MarketWindowEntry.STREAM_CODEC.encode(buf, it) }; buf.writeBoolean(p.capped) },
            { buf -> MarketWindowPayload(List(buf.readVarInt()) { MarketWindowEntry.STREAM_CODEC.decode(buf) }, buf.readBoolean()) },
        )
    }
}

/** Server -> client: an item's price history, prices only, oldest first and at most [MAX_POINTS]. */
data class MarketHistoryPayload(val item: String, val period: String, val prices: List<Float>) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        const val MAX_POINTS = 96
        val TYPE = CustomPacketPayload.Type<MarketHistoryPayload>(LnbEconomy.id("market_history"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MarketHistoryPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeUtf(p.item, 128); buf.writeUtf(p.period, 8)
                buf.writeVarInt(p.prices.size); p.prices.forEach { buf.writeFloat(it) }
            },
            { buf -> MarketHistoryPayload(buf.readUtf(128), buf.readUtf(8), List(buf.readVarInt().coerceAtMost(MAX_POINTS)) { buf.readFloat() }) },
        )
    }
}

/** Server -> client: what became of an order, for the screen's toast. */
data class MarketResultPayload(val ok: Boolean, val message: Component) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<MarketResultPayload>(LnbEconomy.id("market_result"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MarketResultPayload> = StreamCodec.of(
            { buf, p -> buf.writeBoolean(p.ok); ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.message) },
            { buf -> MarketResultPayload(buf.readBoolean(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf)) },
        )
    }
}

/** Client -> server: [OPEN] the screen, [CLOSE] it, [REFRESH] the player's window, or the [HISTORY] of [item]. */
data class MarketRequestPayload(val kind: Int, val item: String, val period: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        const val OPEN = 0
        const val CLOSE = 1
        const val REFRESH = 2
        const val HISTORY = 3
        val PERIODS = listOf("24h", "7d", "30d", "1y")

        val TYPE = CustomPacketPayload.Type<MarketRequestPayload>(LnbEconomy.id("market_request"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MarketRequestPayload> = StreamCodec.of(
            { buf, p -> buf.writeVarInt(p.kind); buf.writeUtf(p.item, 128); buf.writeUtf(p.period, 8) },
            { buf -> MarketRequestPayload(buf.readVarInt(), buf.readUtf(128), buf.readUtf(8)) },
        )
    }
}

/**
 * Client -> server: sell or buy. [expectedTotal] is the client's preview in cents; the server checks and sends it as
 * the order's guard. [account] is the account to pay with (or to credit, when the site allows it), null for the default.
 */
data class MarketOrderPayload(
    val sell: Boolean,
    val item: String,
    val quantity: Int,
    val expectedTotal: Long,
    val account: String?,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<MarketOrderPayload>(LnbEconomy.id("market_order"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MarketOrderPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeBoolean(p.sell); buf.writeUtf(p.item, 128); buf.writeVarInt(p.quantity)
                buf.writeVarLong(p.expectedTotal); buf.writeNullable(p.account) { b, v -> b.writeUtf(v, 64) }
            },
            { buf -> MarketOrderPayload(buf.readBoolean(), buf.readUtf(128), buf.readVarInt(), buf.readVarLong(), buf.readNullable { it.readUtf(64) }) },
        )
    }
}
