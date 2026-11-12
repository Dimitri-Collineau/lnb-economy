package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.LnbEconomy
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * The casino look shared by every lnb-economy screen: green felt, gold frames, crimson actions.
 * Sprites live in `textures/gui/sprites/`; the stretchable ones are nine-slices (see their `.mcmeta`).
 */
object CasinoUi {
    val PANEL = sprite("panel")
    val PANEL_INSET = sprite("panel_inset")
    val PLAQUE = sprite("plaque")
    val BRACKET_LEFT = sprite("bracket_left")
    val BRACKET_RIGHT = sprite("bracket_right")
    val ACCOUNT_PERSONAL = sprite("account_personal")
    val ACCOUNT_SHARED = sprite("account_shared")
    val STAR = sprite("star")
    val STAR_EMPTY = sprite("star_empty")
    val TEXT_FIELD = sprite("text_field")
    val TEXT_FIELD_HIGHLIGHTED = sprite("text_field_highlighted")
    val PAGE_FORWARD = sprite("page_forward")
    val PAGE_FORWARD_DISABLED = sprite("page_forward_disabled")
    val PAGE_BACKWARD = sprite("page_backward")
    val PAGE_BACKWARD_DISABLED = sprite("page_backward_disabled")
    val TAB = sprite("tab")
    val TAB_SELECTED = sprite("tab_selected")
    val SLOT = sprite("slot")
    val LIGHT_ON = sprite("light_on")
    val LIGHT_OFF = sprite("light_off")

    /** Normal, highlighted (hovered or focused) and disabled sprites of a button family. */
    class ButtonSprites(name: String) {
        val normal = sprite(name)
        val highlighted = sprite("${name}_highlighted")
        val disabled = sprite("${name}_disabled")

        fun get(active: Boolean, highlighted: Boolean): ResourceLocation = when {
            !active -> disabled
            highlighted -> this.highlighted
            else -> normal
        }
    }

    val PRIMARY = ButtonSprites("button")
    val SECONDARY = ButtonSprites("button_secondary")

    const val GOLD_TEXT = 0xFFFFD45A.toInt()
    const val IVORY = 0xFFF0E8D0.toInt()
    const val DIM = 0xFFB0BEAC.toInt()
    const val GREY = 0xFF808080.toInt()
    /** Placeholder text inside an empty text field. */
    const val HINT = 0xFF5A6E60.toInt()
    const val TITLE_OUTLINE = 0xFF140E06.toInt()

    /** Bracket sprites are 7x13; the container's top edge is their 9th row. */
    const val BRACKET_WIDTH = 7
    const val BRACKET_HEIGHT = 13
    const val BRACKET_ABOVE = 8

    private fun sprite(name: String): ResourceLocation = LnbEconomy.id(name)

    fun panel(graphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
        graphics.blitSprite(PANEL, x, y, width, height)
    }

    /** [text] cut with an ellipsis so it is at most [width] pixels wide. */
    fun fit(font: Font, text: String, width: Int): String =
        if (font.width(text) <= width) text else font.plainSubstrByWidth(text, maxOf(0, width - font.width("..."))) + "..."

    /**
     * A borderless text box meant to sit on the [TEXT_FIELD] sprite, which the screen draws itself with [field]
     * at ([x], [y]) over [width] x 18.
     */
    fun editBox(font: Font, x: Int, y: Int, width: Int, hint: Component, maxLength: Int): net.minecraft.client.gui.components.EditBox {
        val box = net.minecraft.client.gui.components.EditBox(font, x + 5, y + 5, width - 10, 10, hint)
        box.setBordered(false)
        box.setTextColor(IVORY)
        box.setMaxLength(maxLength)
        box.setHint(hint.copy().withColor(HINT))
        return box
    }

    /** The sunken field behind a box made by [editBox]. */
    fun field(graphics: GuiGraphics, box: net.minecraft.client.gui.components.EditBox) {
        if (!box.visible) return
        graphics.blitSprite(if (box.isFocused) TEXT_FIELD_HIGHLIGHTED else TEXT_FIELD, box.x - 5, box.y - 5, box.width + 10, 18)
    }

    /** A window title: gold letters ringed with a dark outline, centered on [centerX]. */
    fun title(graphics: GuiGraphics, font: Font, text: Component, centerX: Int, y: Int) {
        val x = centerX - font.width(text) / 2
        font.drawInBatch8xOutline(
            text.visualOrderText, x.toFloat(), y.toFloat(), GOLD_TEXT, TITLE_OUTLINE,
            graphics.pose().last().pose(), graphics.bufferSource(), 0xF000F0,
        )
        graphics.flush()
    }
}
