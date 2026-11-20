package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.client.ClientMarket
import fr.bde_teknantes.lnb.economy.money.Amounts
import fr.bde_teknantes.lnb.economy.network.MarketRequestPayload
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor
import kotlin.math.roundToInt

/**
 * The player's efficiency per tier: how much they have sold in the window (the meter fills to `nominal / q2`, with a
 * tick at `q1 / q2`), the rate they are paid now, and how much is left at it. `nominal` is the value before the
 * reduction, so the meter measures what was sold, not what was paid.
 */
class MarketEfficiencyScreen(private val parent: Screen) : Screen(Component.translatable("gui.lnbeconomy.market.efficiency")) {
    private companion object {
        const val W = 312
        const val TILE_H = 54
        const val TILE_GAP = 6
    }

    override fun init() {
        MarketUi.switching = false
        PacketDistributor.sendToServer(MarketRequestPayload(MarketRequestPayload.REFRESH, "", ""))
        val tiers = ClientMarket.catalog?.tiers.orEmpty()
        val height = 40 + tiers.size * (TILE_H + TILE_GAP) + 30
        val x = (width - W) / 2
        val y = (this.height - height) / 2
        addRenderableWidget(MarketUi.Button(x + 8, y + height - 24, 70, 16, CommonComponents.GUI_BACK, MarketUi.Button.Kind.NORMAL) { onClose() })
    }

    /** Drawn here, not in render: the game blurs what a screen drew before its background step. */
    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderTransparentBackground(graphics)
        val tiers = ClientMarket.catalog?.tiers.orEmpty()
        val height = 40 + tiers.size * (TILE_H + TILE_GAP) + 30
        val x = (width - W) / 2
        val y = (this.height - height) / 2
        PixelBox.raised(graphics, x, y, W, height, PixelBox.SURFACE)
        graphics.drawString(font, title, x + 10, y + 10, PixelBox.TEXT, false)
        val language = MarketUi.language()
        CasinoUi.wrapped(graphics, font, Component.translatable("gui.lnbeconomy.market.efficiency.help"), x + 10, y + 22, W - 20, 1, PixelBox.MUTED)
        tiers.forEachIndexed { i, tier ->
            val ty = y + 36 + i * (TILE_H + TILE_GAP)
            PixelBox.inset(graphics, x + 8, ty, W - 16, TILE_H)
            val mine = ClientMarket.windowEntry(tier.tier)
            graphics.drawString(font, Component.translatable("gui.lnbeconomy.market.tier", tier.tier), x + 16, ty + 6, PixelBox.TEXT, false)
            val rate = mine?.rate ?: 1.0
            val rateText = "${(rate * 100).roundToInt()} %"
            graphics.drawString(font, rateText, x + W - 16 - font.width(rateText), ty + 6, if (rate >= 1.0) PixelBox.GREEN else PixelBox.GOLD, false)
            // The meter.
            val mx = x + 16
            val mw = W - 32
            val q2 = mine?.q2?.takeIf { it > 0 } ?: (tier.q2 / 100.0).coerceAtLeast(1.0)
            val q1 = mine?.q1 ?: (tier.q1 / 100.0)
            PixelBox.flat(graphics, mx, ty + 20, mw, 8, PixelBox.BG)
            val fill = ((mine?.nominal ?: 0.0) / q2).coerceIn(0.0, 1.0)
            graphics.fill(mx + 1, ty + 21, mx + 1 + ((mw - 2) * fill).toInt(), ty + 27, PixelBox.DUSK)
            val tick = mx + 1 + ((mw - 2) * (q1 / q2).coerceIn(0.0, 1.0)).toInt()
            graphics.fill(tick, ty + 18, tick + 1, ty + 30, PixelBox.TEXT)
            val left = mine?.toNextBracket
            val next = mine?.nextRate
            val line = if (left != null && next != null) {
                Component.translatable(
                    "gui.lnbeconomy.market.efficiency.left",
                    Amounts.format(Amounts.fromLnbits(left.toLong().coerceAtLeast(0)), language), rateText, "${(next * 100).roundToInt()} %",
                )
            } else Component.translatable("gui.lnbeconomy.market.efficiency.last")
            graphics.drawString(font, CasinoUi.fit(font, line.string, W - 32), x + 16, ty + 36, PixelBox.MUTED, false)
        }
        if (ClientMarket.capped) {
            graphics.drawString(font, Component.translatable("economy.lnbeconomy.market.capped"), x + 90, y + height - 20, PixelBox.GOLD, false)
        }
    }

    override fun onClose() {
        MarketUi.go(parent)
    }

    override fun removed() = MarketUi.left()

    override fun isPauseScreen() = false
}
