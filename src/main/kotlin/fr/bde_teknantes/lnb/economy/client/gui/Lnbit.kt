package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.money.Amounts
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

/** The LNBit coin, drawn by the `lnbeconomy:icons` font in place of [Amounts.SYMBOL]. */
object Lnbit {
    private val FONT = LnbEconomy.id("icons")
    private const val WHITE = 0xFFFFFF

    /** The coin alone. Always white, so the color of the surrounding text does not tint it. */
    fun symbol(): MutableComponent =
        Component.literal(Amounts.SYMBOL).withStyle { it.withFont(FONT).withColor(WHITE) }

    /** The coin then `1 250`, for the inventory and block screens. A style set on the result colors the amount. */
    fun compact(amount: Long, language: String): MutableComponent =
        Component.empty().append(symbol()).append(" " + Amounts.format(amount, language))
}
