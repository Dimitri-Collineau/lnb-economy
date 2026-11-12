package fr.bde_teknantes.lnb.economy.client.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

/** A vanilla [Button] drawn with the casino sprites: crimson for actions, dark felt for the rest. */
class CasinoButton(
    x: Int, y: Int, width: Int, height: Int,
    message: Component,
    private val sprites: CasinoUi.ButtonSprites,
    onPress: OnPress,
) : Button(x, y, width, height, message, onPress, DEFAULT_NARRATION) {

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.setColor(1f, 1f, 1f, alpha)
        graphics.blitSprite(sprites.get(active, isHoveredOrFocused), x, y, width, height)
        graphics.setColor(1f, 1f, 1f, 1f)
        val color = if (active) CasinoUi.IVORY else CasinoUi.GREY
        renderString(graphics, Minecraft.getInstance().font, color or (Math.round(alpha * 255f) shl 24))
    }

    companion object {
        fun primary(message: Component, onPress: OnPress) = Builder(message, onPress, CasinoUi.PRIMARY)
        fun secondary(message: Component, onPress: OnPress) = Builder(message, onPress, CasinoUi.SECONDARY)
    }

    class Builder(private val message: Component, private val onPress: OnPress, private val sprites: CasinoUi.ButtonSprites) {
        private var x = 0
        private var y = 0
        private var width = 150
        private var height = 20

        fun bounds(x: Int, y: Int, width: Int, height: Int) = apply {
            this.x = x; this.y = y; this.width = width; this.height = height
        }

        fun build() = CasinoButton(x, y, width, height, message, sprites, onPress)
    }
}
