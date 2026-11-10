package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.network.LinkOption
import fr.bde_teknantes.lnb.economy.network.LinkPayload
import fr.bde_teknantes.lnb.economy.network.OpenLinkPayload
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

/** Chooses the casino of a machine or a counter, and a machine's largest bet. */
class LinkScreen(private val link: OpenLinkPayload) : Screen(
    if (link.game == null) Component.translatable("gui.lnbeconomy.link.counter")
    else Component.translatable("gui.lnbeconomy.link.machine", Component.translatable("gui.lnbeconomy.game.${link.game}")),
) {
    private companion object {
        const val WIDTH = 240
    }

    private var chosen: LinkOption? = link.options.firstOrNull { it.handle == link.current } ?: link.options.firstOrNull()
    private var maxBet: EditBox? = null

    override fun init() {
        val top = height / 2 - 70
        val left = (width - WIDTH) / 2
        var y = top + 24
        if (link.options.isNotEmpty()) {
            addRenderableWidget(
                CycleButton.builder<LinkOption> { Component.literal("${it.name} (#${it.handle})") }
                    .withValues(link.options)
                    .withInitialValue(chosen ?: link.options.first())
                    .create(left, y, WIDTH, 20, Component.translatable("gui.lnbeconomy.link.casino")) { _, value -> chosen = value },
            )
            y += 36
            if (link.game != null) {
                val box = EditBox(font, left, y, WIDTH, 20, Component.translatable("gui.lnbeconomy.link.max_bet"))
                box.setFilter { text -> text.isEmpty() || (text.length <= 13 && text.all(Char::isDigit)) }
                box.setHint(Component.translatable("gui.lnbeconomy.link.max_bet.none").withStyle(ChatFormatting.DARK_GRAY))
                box.value = link.maxBet?.toString() ?: ""
                maxBet = addRenderableWidget(box)
                y += 28
            }
            addRenderableWidget(Button.builder(Component.translatable("gui.lnbeconomy.link.save")) {
                val handle = chosen?.handle ?: return@builder
                PacketDistributor.sendToServer(LinkPayload(link.pos, handle, maxBet?.value?.toLongOrNull()?.takeIf { it > 0 }))
                onClose()
            }.bounds(left, y, WIDTH, 20).build())
            y += 24
        }
        if (link.mayUnlink) {
            addRenderableWidget(Button.builder(Component.translatable("gui.lnbeconomy.link.unlink")) {
                PacketDistributor.sendToServer(LinkPayload(link.pos, null, null))
                onClose()
            }.bounds(left, y, WIDTH, 20).build())
            y += 24
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL) { onClose() }.bounds(left, y + 4, WIDTH, 20).build())
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val top = height / 2 - 70
        val left = (width - WIDTH) / 2
        graphics.drawCenteredString(font, title, width / 2, top - 14, 0xFFFFFF)
        val current = link.current?.let { Component.translatable("gui.lnbeconomy.link.current", "#$it") }
            ?: Component.translatable("gui.lnbeconomy.link.none")
        graphics.drawCenteredString(font, current, width / 2, top + 4, 0xA0A0A0)
        if (link.options.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.lnbeconomy.link.locked"), width / 2, top + 28, 0xFF6060)
        } else if (link.game != null) {
            graphics.drawString(font, Component.translatable("gui.lnbeconomy.link.max_bet"), left, top + 50, 0xFFD45A)
        }
    }

    override fun isPauseScreen(): Boolean = false
}
