package fr.bde_teknantes.lnb.economy.client.gui

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

/**
 * Shown when the Market is off on the site (or can't be read yet). The server keeps reading its state, and the Market
 * screen replaces this one by itself when it opens.
 */
class MarketClosedScreen : Screen(Component.translatable("gui.lnbeconomy.market.title")) {
    private companion object {
        const val W = 220
        const val H = 90
    }

    override fun init() {
        MarketUi.switching = false
        val x = (width - W) / 2
        val y = (height - H) / 2
        addRenderableWidget(MarketUi.Button(x + (W - 80) / 2, y + H - 26, 80, 18, CommonComponents.GUI_DONE, MarketUi.Button.Kind.NORMAL) { onClose() })
    }

    /** Drawn here, not in render: the game blurs what a screen drew before its background step. */
    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderTransparentBackground(graphics)
        val x = (width - W) / 2
        val y = (height - H) / 2
        PixelBox.raised(graphics, x, y, W, H, PixelBox.SURFACE)
        graphics.drawString(font, title, x + 10, y + 10, PixelBox.TEXT, false)
        CasinoUi.wrapped(graphics, font, Component.translatable("gui.lnbeconomy.market.closed"), x + 10, y + 28, W - 20, 3, PixelBox.MUTED)
    }

    override fun removed() = MarketUi.left()

    override fun isPauseScreen() = false
}
