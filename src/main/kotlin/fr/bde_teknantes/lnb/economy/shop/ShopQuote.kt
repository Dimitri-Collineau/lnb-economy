package fr.bde_teknantes.lnb.economy.shop

/**
 * What a customer can do at a shop: how many lots it has in stock, how many it would buy back from what the customer
 * carries, and what each costs. [buyPrice] 0 means the shop doesn't buy back; [room] is how many more items it takes.
 */
data class ShopQuote(
    val lotSize: Int,
    val sellPrice: Long,
    val buyPrice: Long,
    val stock: Int,
    val room: Int,
    val carried: Int,
) {
    val buysBack: Boolean get() = buyPrice > 0

    val maxBuyLots: Int get() = if (lotSize > 0) stock / lotSize else 0

    val maxSellLots: Int get() = if (buysBack && lotSize > 0) minOf(carried, room) / lotSize else 0

    fun buyTotal(lots: Int): Long = sellPrice * lots

    fun sellTotal(lots: Int): Long = buyPrice * lots

    /** [lots] kept between 1 and the most the customer can buy or sell, whichever is larger. */
    fun clamp(lots: Int): Int = lots.coerceIn(1, maxOf(1, maxBuyLots, maxSellLots))
}
