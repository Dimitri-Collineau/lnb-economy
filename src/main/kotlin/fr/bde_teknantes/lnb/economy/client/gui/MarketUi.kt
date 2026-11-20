package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.network.MarketRequestPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

/** What the three Market screens share: leaving, and the pixel button. */
object MarketUi {
    /** True while one Market screen replaces another: the player is still at the Market. */
    var switching = false

    /** The player closed the Market: the server stops sending. */
    fun left() {
        if (!switching) PacketDistributor.sendToServer(MarketRequestPayload(MarketRequestPayload.CLOSE, "", ""))
    }

    /** Opens [next] without telling the server the Market was left. */
    fun go(next: Screen?) {
        switching = true
        Minecraft.getInstance().setScreen(next)
        switching = false
    }

    fun language(): String = Minecraft.getInstance().options.languageCode

    private const val SELECTED = 0xFF343C94.toInt()

    /** A button in the B2 look. */
    class Button(
        x: Int, y: Int, width: Int, height: Int,
        message: Component,
        private val kind: Kind,
        private val press: () -> Unit,
    ) : AbstractButton(x, y, width, height, message) {
        enum class Kind { NORMAL, ACTION, TAB, TAB_ON, CHIP, CHIP_ON }

        var kindNow: Kind = kind

        override fun onPress() = press()

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val hover = isHoveredOrFocused && active
            when (kindNow) {
                Kind.ACTION -> PixelBox.raised(graphics, x, y, width, height, if (!active) PixelBox.BORDER else if (hover) PixelBox.ACTION_HOVER else PixelBox.ACTION)
                // The open tab is bright and lit; the other is flat and dim, so which one you are on is plain.
                Kind.TAB_ON -> {
                    PixelBox.outlined(graphics, x, y, width, height, SELECTED, PixelBox.DUSK)
                    graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 1, PixelBox.DUSK)
                }
                Kind.CHIP_ON -> PixelBox.outlined(graphics, x, y, width, height, SELECTED, PixelBox.DUSK)
                Kind.TAB -> PixelBox.flat(graphics, x, y, width, height, if (hover) PixelBox.RAISED else PixelBox.SURFACE)
                Kind.CHIP -> PixelBox.raised(graphics, x, y, width, height, if (hover) PixelBox.BORDER else PixelBox.RAISED)
                Kind.NORMAL -> PixelBox.raised(graphics, x, y, width, height, if (hover) PixelBox.BORDER else PixelBox.RAISED)
            }
            val font = Minecraft.getInstance().font
            val color = when {
                !active || kindNow == Kind.TAB -> PixelBox.MUTED
                kindNow == Kind.TAB_ON || kindNow == Kind.CHIP_ON -> 0xFFFFFFFF.toInt()
                else -> PixelBox.TEXT
            }
            val text = CasinoUi.fit(font, message.string, width - 6)
            graphics.drawString(font, text, x + (width - font.width(text)) / 2, y + (height - 8) / 2, color, false)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }
}
