package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.counter.CounterWood
import fr.bde_teknantes.lnb.economy.network.CounterWoodPayload
import fr.bde_teknantes.lnb.economy.network.LinkOption
import fr.bde_teknantes.lnb.economy.network.LinkPayload
import fr.bde_teknantes.lnb.economy.network.OpenLinkPayload
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor

/** Chooses the casino of a machine or a counter, a machine's largest bet, and the wood of a counter's wall. */
class LinkScreen(private val link: OpenLinkPayload) : Screen(
    if (link.game == null) Component.translatable("gui.lnbeconomy.link.counter")
    else Component.translatable("gui.lnbeconomy.link.machine", Component.translatable("gui.lnbeconomy.game.${link.game}")),
) {
    private companion object {
        const val WIDTH = 240
        const val WOOD_SIZE = 20
        const val WOOD_STEP = 22
    }

    private var chosen: LinkOption? = link.options.firstOrNull { it.handle == link.current } ?: link.options.firstOrNull()
    private var maxBet: EditBox? = null
    private var wood: CounterWood? = link.wood?.let(CounterWood::byId)
    private var woodLabelY = -1

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
        if (wood != null) {
            woodLabelY = y + 4
            y += 16
            CounterWood.entries.forEachIndexed { i, choice -> addRenderableWidget(WoodChoice(choice, left + i * WOOD_STEP, y)) }
            y += 22
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL) { onClose() }.bounds(left, y + 4, WIDTH, 20).build())
    }

    /** One wood for the counter's wall, shown by its planks; a click rebuilds the wall in it right away. */
    private inner class WoodChoice(private val choice: CounterWood, x: Int, y: Int) : AbstractButton(x, y, WOOD_SIZE, WOOD_SIZE, Component.empty()) {
        private val stack = ItemStack(choice.planks())

        init {
            message = stack.hoverName
            setTooltip(Tooltip.create(stack.hoverName))
        }

        override fun onPress() {
            wood = choice
            PacketDistributor.sendToServer(CounterWoodPayload(link.pos, choice.serializedName))
        }

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            graphics.blitSprite(CasinoUi.SECONDARY.get(true, wood == choice || isHoveredOrFocused), x, y, width, height)
            graphics.renderFakeItem(stack, x + 2, y + 2)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
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
        if (woodLabelY >= 0) graphics.drawString(font, Component.translatable("gui.lnbeconomy.link.wood"), left, woodLabelY, 0xFFD45A)
    }

    override fun isPauseScreen(): Boolean = false
}
