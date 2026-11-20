package fr.bde_teknantes.lnb.economy.market

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The Market's prices, as section 7 of the site's `market-mod.md` computes them, without Minecraft. The site decides;
 * these numbers are a preview, and differ from the site's by a few cents at most. Prices are LNBits per item
 * (`Double`, possibly below one cent); totals are integer cents.
 */
object MarketPricing {
    /** One item's curve, from `GET /state`. Stock and thresholds are in the units of the formulas (LNBits for thresholds). */
    data class Curve(
        val p0: Double,
        val pMin: Double,
        val pMax: Double,
        val s0: Double,
        val sMin: Double,
        val sMax: Double,
        val k: Double,
        val alpha: Double,
    ) {
        /** Below this stock the price is flat at [pMax]. */
        val sA: Double get() = s0 * (p0 / pMax).pow(1 / k)

        /** Above this stock the price is flat at [pMin]. */
        val sB: Double get() = s0 * (p0 / pMin).pow(1 / k)

        private val logarithmic: Boolean get() = kotlin.math.abs(1 - k) < 1e-9

        fun pRef(s: Double): Double = min(pMax, max(pMin, p0 * (s0 / s).pow(k)))

        private fun f(s: Double): Double =
            if (logarithmic) p0 * s0 * ln(s) else p0 * s0.pow(k) * s.pow(1 - k) / (1 - k)

        /** The integral of [pRef] from stock [a] to [b]. Continuous, so a sale split in several pays the same. */
        fun integral(a: Double, b: Double): Double {
            if (b <= a) return 0.0
            val sA = sA
            val sB = sB
            var total = pMax * max(0.0, min(b, sA) - max(a, 0.0))
            if (min(b, sB) > max(a, sA)) total += f(min(b, sB)) - f(max(a, sA))
            total += pMin * max(0.0, b - max(a, sB))
            return total
        }

        /** The `b >= a` with `integral(a, b) = cost`. */
        fun inverse(a: Double, cost: Double): Double {
            var from = a
            var left = cost
            val sA = sA
            val sB = sB
            if (from < sA) {
                val room = pMax * (sA - from)
                if (left <= room) return from + left / pMax
                left -= room
                from = sA
            }
            if (from < sB) {
                val room = f(sB) - f(from)
                if (left <= room) {
                    return if (logarithmic) from * exp(left / (p0 * s0))
                    else (from.pow(1 - k) + left * (1 - k) / (p0 * s0.pow(k))).pow(1 / (1 - k))
                }
                left -= room
                from = sB
            }
            return from + left / pMin
        }

        /** The stock [stock], measured at [hoursSince] hours ago, drifted back toward [s0]. Never negative time. */
        fun drift(stock: Double, hoursSince: Double): Double = s0 + (stock - s0) * (1 - alpha).pow(max(0.0, hoursSince))
    }

    /** A player's tier window: thresholds in LNBits of nominal value, rates as fractions. */
    data class Window(val nominal: Double, val q1: Double, val q2: Double, val mid: Double, val tail: Double)

    data class Purchase(val totalCents: Long, val newStock: Double, val perItem: Double)

    /** One part of a sale at a single [rate]: [quantity] items (fractional at a boundary) paying [totalCents]. */
    data class Bracket(val rate: Double, val quantity: Double, val totalCents: Long)

    data class Sale(
        val totalCents: Long,
        val newStock: Double,
        /** The window after the sale, in LNBits of nominal value. */
        val windowAfter: Double,
        val brackets: List<Bracket>,
        /** The payout was cut to the mint budget. */
        val capped: Boolean,
    )

    private const val EPSILON = 1e-6

    /** A purchase of [quantity] items from stock [stock] (already drifted), [sigma] the market's spread. */
    fun buy(curve: Curve, sigma: Double, stock: Double, quantity: Int): Purchase {
        val d = min(quantity.toDouble(), max(0.0, stock - curve.sMin))
        val cost = (1 + sigma) * (curve.integral(stock - d, stock) + (quantity - d) * curve.pRef(curve.sMin))
        val total = ceil(cost * 100 - EPSILON).toLong()
        return Purchase(total, stock - d, if (quantity > 0) total / 100.0 / quantity else 0.0)
    }

    /** A sale of [quantity] items from stock [stock] (already drifted) by a player whose window is [window]. */
    fun sell(curve: Curve, sigma: Double, stock: Double, quantity: Int, window: Window, mintRemaining: Long = Long.MAX_VALUE): Sale {
        var s = stock
        var w = window.nominal
        var q = quantity.toDouble()
        var pay = 0.0
        val parts = ArrayList<Triple<Double, Double, Double>>() // rate, quantity, pay
        var guard = 0

        while (q > 1e-9 && guard++ < 50) {
            val r: Double
            val cap: Double
            val threshold: Double
            when {
                w < window.q1 -> { r = 1.0; cap = window.q1 - w; threshold = window.q1 }
                w < window.q2 -> { r = window.mid; cap = window.q2 - w; threshold = window.q2 }
                else -> { r = window.tail; cap = Double.POSITIVE_INFINITY; threshold = Double.POSITIVE_INFINITY }
            }

            if (s >= curve.sMax) {
                val p = curve.pRef(curve.sMax)
                val n = min(q, cap / p)
                val paid = (1 - sigma) * r * n * p
                parts += Triple(r, n, paid)
                pay += paid
                if (n == q) {
                    w += n * p
                    q = 0.0
                } else {
                    w = threshold
                    q -= n
                }
                continue
            }

            var d = r * q
            var reachedMax = false
            var endsHere = false
            if (d >= curve.sMax - s) {
                d = curve.sMax - s
                reachedMax = true
            }
            if (curve.integral(s, s + d) / r > cap) {
                d = curve.inverse(s, cap * r) - s
                endsHere = true
                reachedMax = false
            }
            val v = curve.integral(s, s + d)
            val paid = (1 - sigma) * v
            pay += paid
            w = if (endsHere) threshold else w + v / r
            val consumed = if (endsHere || reachedMax) d / r else q
            parts += Triple(r, consumed, paid)
            q = if (endsHere || reachedMax) q - d / r else 0.0
            s = if (reachedMax) curve.sMax else s + d
        }

        val uncapped = floor(pay * 100 + EPSILON).toLong()
        val total = min(uncapped, mintRemaining)
        return Sale(total, s, w, brackets(parts, uncapped, total), capped = total < uncapped)
    }

    /** Adjacent parts at one rate merge; part totals are cumulative floors and the last takes the remainder. */
    private fun brackets(parts: List<Triple<Double, Double, Double>>, uncapped: Long, total: Long): List<Bracket> {
        val merged = ArrayList<Triple<Double, Double, Double>>()
        for (part in parts) {
            val last = merged.lastOrNull()
            if (last != null && last.first == part.first) merged[merged.size - 1] = Triple(last.first, last.second + part.second, last.third + part.third)
            else merged += part
        }
        var cumulative = 0.0
        var given = 0L
        return merged.mapIndexed { i, (rate, quantity, paid) ->
            cumulative += paid
            val part = if (i == merged.size - 1) total - given else min(floor(cumulative * 100 + EPSILON).toLong(), uncapped) - given
            given += part
            Bracket(rate, quantity, part)
        }
    }
}
