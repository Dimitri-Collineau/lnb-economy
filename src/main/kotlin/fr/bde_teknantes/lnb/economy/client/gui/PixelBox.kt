package fr.bde_teknantes.lnb.economy.client.gui

import net.minecraft.client.gui.GuiGraphics

/**
 * The Market's look ("B2"): flat night-blue boxes with a one-pixel step on the corners and a light and a dark edge, the
 * way the game draws its own interface, in the colors of LNB's site. Everything is `fill` calls: no textures.
 */
object PixelBox {
    const val BG = 0xFF0B0E27.toInt()
    const val SURFACE = 0xFF13173A.toInt()
    const val RAISED = 0xFF1C2150.toInt()
    const val BORDER = 0xFF282F66.toInt()
    const val TEXT = 0xFFE9EBF8.toInt()
    const val MUTED = 0xFFA0A6D0.toInt()
    const val ACTION = 0xFFB82238.toInt()
    const val ACTION_HOVER = 0xFFD02A42.toInt()
    const val RED = 0xFFF25C70.toInt()
    const val GREEN = 0xFF4ADE80.toInt()
    const val DUSK = 0xFFA99BFF.toInt()
    const val GOLD = 0xFFFFD45A.toInt()
    private const val OUTLINE = 0xFF05071A.toInt()
    private const val LIGHT = 0x33FFFFFF
    private const val SHADE = 0x55000000

    /** A box with cut corners: a dark outline, then [fill]. */
    fun flat(g: GuiGraphics, x: Int, y: Int, w: Int, h: Int, fill: Int, outline: Int = OUTLINE) {
        g.fill(x + 1, y, x + w - 1, y + h, outline)
        g.fill(x, y + 1, x + w, y + h - 1, outline)
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill)
    }

    /** A box that stands out: light on its top and left edges, dark on the bottom and right. */
    fun raised(g: GuiGraphics, x: Int, y: Int, w: Int, h: Int, fill: Int = RAISED) {
        flat(g, x, y, w, h, fill)
        g.fill(x + 1, y + 1, x + w - 1, y + 2, LIGHT)
        g.fill(x + 1, y + 2, x + 2, y + h - 1, LIGHT)
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, SHADE)
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, SHADE)
    }

    /** A box that sinks in: dark on its top and left edges, light on the bottom and right. */
    fun inset(g: GuiGraphics, x: Int, y: Int, w: Int, h: Int, fill: Int = BG) {
        flat(g, x, y, w, h, fill)
        g.fill(x + 1, y + 1, x + w - 1, y + 2, SHADE)
        g.fill(x + 1, y + 2, x + 2, y + h - 1, SHADE)
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, LIGHT)
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, LIGHT)
    }

    /** A box with only a colored one-pixel edge, for a selected tab or a focused field. */
    fun outlined(g: GuiGraphics, x: Int, y: Int, w: Int, h: Int, fill: Int, edge: Int) {
        flat(g, x, y, w, h, edge)
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill)
    }
}
