package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.counter.CounterWood
import fr.bde_teknantes.lnb.economy.network.CounterWoodPayload
import fr.bde_teknantes.lnb.economy.network.LinkOption
import fr.bde_teknantes.lnb.economy.network.LinkPayload
import fr.bde_teknantes.lnb.economy.network.OpenLinkPayload
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
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
        const val PANEL_WIDTH = 280
        const val SECTION_LEFT = 14
        const val SECTION_WIDTH = PANEL_WIDTH - 2 * SECTION_LEFT
        const val PAD = 6
        const val INNER_WIDTH = SECTION_WIDTH - 2 * PAD
        const val GAP = 4
        const val ROW = 18
        const val WOOD_SIZE = 20
        const val WOOD_STEP = 22
        const val HEADER = 38
        const val FOOTER = 44
        const val LOCKED_RED = 0xFFFF6060.toInt()
    }

    private val isCounter = link.game == null
    private var chosen: LinkOption? = link.options.firstOrNull { it.handle == link.current } ?: link.options.firstOrNull()
    private var maxBet: EditBox? = null
    private var wood: CounterWood? = link.wood?.let(CounterWood::byId)

    private var panelX = 0
    private var panelY = 0
    private var panelHeight = 0

    /** Felt sections, their titles and the plain lines, laid out by [init] and drawn by the render methods. */
    private data class Section(val y: Int, val height: Int, val title: Component)
    private val sections = mutableListOf<Section>()
    private var lockedLines = emptyList<net.minecraft.util.FormattedCharSequence>()
    private var lockedY = 0

    override fun init() {
        sections.clear()
        lockedLines = if (link.options.isEmpty()) font.split(Component.translatable("gui.lnbeconomy.link.locked"), INNER_WIDTH) else emptyList()

        // First pass: how tall the content is, so the window can be centered.
        var contentHeight = HEADER
        if (link.options.isNotEmpty()) {
            contentHeight += (if (isCounter) 16 + ROW + GAP + ROW + PAD else 16 + ROW + PAD) + GAP
            if (!isCounter) contentHeight += 16 + ROW + PAD + GAP + ROW + GAP
        } else {
            contentHeight += lockedLines.size * 10 + GAP
            if (link.mayUnlink) contentHeight += ROW + GAP
        }
        if (wood != null) contentHeight += 18 + WOOD_SIZE + PAD + GAP
        panelHeight = contentHeight + FOOTER
        panelX = (width - PANEL_WIDTH) / 2
        panelY = (height - panelHeight) / 2

        val sectionX = panelX + SECTION_LEFT
        val innerX = sectionX + PAD
        var y = panelY + HEADER
        if (link.options.isNotEmpty()) {
            val casinoHeight = if (isCounter) 16 + ROW + GAP + ROW + PAD else 16 + ROW + PAD
            sections += Section(y, casinoHeight, Component.translatable("gui.lnbeconomy.link.casino"))
            addRenderableWidget(CasinoCycle(innerX, y + 16))
            if (isCounter) actions(innerX, y + 16 + ROW + GAP)
            y += casinoHeight + GAP
            if (!isCounter) {
                sections += Section(y, 16 + ROW + PAD, Component.translatable("gui.lnbeconomy.link.max_bet"))
                val box = EditBox(font, innerX + 5, y + 16 + 5, INNER_WIDTH - 10, 10, Component.translatable("gui.lnbeconomy.link.max_bet"))
                box.setBordered(false)
                box.setTextColor(CasinoUi.IVORY)
                box.setFilter { text -> text.isEmpty() || (text.length <= 13 && text.all(Char::isDigit)) }
                box.setHint(Component.translatable("gui.lnbeconomy.link.max_bet.none").withColor(CasinoUi.HINT))
                box.value = maxBet?.value ?: link.maxBet?.toString() ?: ""
                maxBet = addRenderableWidget(box)
                y += 16 + ROW + PAD + GAP
                actions(innerX, y)
                y += ROW + GAP
            }
        } else {
            lockedY = y
            y += lockedLines.size * 10 + GAP
            if (link.mayUnlink) {
                addRenderableWidget(unlinkButton(innerX, y, INNER_WIDTH))
                y += ROW + GAP
            }
        }
        if (wood != null) {
            sections += Section(y, 18 + WOOD_SIZE + PAD, Component.translatable("gui.lnbeconomy.link.wood"))
            CounterWood.entries.forEachIndexed { i, choice -> addRenderableWidget(WoodChoice(choice, innerX + i * WOOD_STEP, y + 18)) }
        }
        addRenderableWidget(
            CasinoButton.secondary(CommonComponents.GUI_CANCEL) { onClose() }
                .bounds(width / 2 - 45, panelY + panelHeight - 38, 90, ROW).build(),
        )
    }

    /** "Link", and "Unlink" beside it when the player may remove the current link. */
    private fun actions(x: Int, y: Int) {
        val linkWidth = if (link.mayUnlink) (INNER_WIDTH - GAP) / 2 else INNER_WIDTH
        addRenderableWidget(
            CasinoButton.primary(Component.translatable("gui.lnbeconomy.link.save")) {
                val handle = chosen?.handle ?: return@primary
                PacketDistributor.sendToServer(LinkPayload(link.pos, handle, maxBet?.value?.toLongOrNull()?.takeIf { it > 0 }))
                onClose()
            }.bounds(x, y, linkWidth, ROW).build(),
        )
        if (link.mayUnlink) addRenderableWidget(unlinkButton(x + linkWidth + GAP, y, INNER_WIDTH - linkWidth - GAP))
    }

    private fun unlinkButton(x: Int, y: Int, w: Int) =
        CasinoButton.secondary(Component.translatable("gui.lnbeconomy.link.unlink")) {
            PacketDistributor.sendToServer(LinkPayload(link.pos, null, null))
            onClose()
        }.bounds(x, y, w, ROW).build()

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        CasinoUi.panel(graphics, panelX, panelY, PANEL_WIDTH, panelHeight)
        for (s in sections) graphics.blitSprite(CasinoUi.PANEL_INSET, panelX + SECTION_LEFT, s.y, SECTION_WIDTH, s.height)
        maxBet?.let {
            val sprite = if (it.isFocused) CasinoUi.TEXT_FIELD_HIGHLIGHTED else CasinoUi.TEXT_FIELD
            graphics.blitSprite(sprite, panelX + SECTION_LEFT + PAD, it.y - 5, INNER_WIDTH, ROW)
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        CasinoUi.title(graphics, font, title, width / 2, panelY + 11)
        val kind = if (isCounter) ".counter" else ""
        val status = link.current?.let { Component.translatable("gui.lnbeconomy.link.current$kind", "#$it") }
            ?: Component.translatable("gui.lnbeconomy.link.none$kind")
        graphics.drawCenteredString(font, status, width / 2, panelY + 25, CasinoUi.DIM)
        val innerX = panelX + SECTION_LEFT + PAD
        for (s in sections) graphics.drawString(font, s.title, innerX, s.y + 5, CasinoUi.GOLD_TEXT, true)
        lockedLines.forEachIndexed { i, line -> graphics.drawCenteredString(font, line, width / 2, lockedY + i * 10, LOCKED_RED) }
    }

    override fun isPauseScreen(): Boolean = false

    /** The casino to link to: name and handle. A click moves to the next casino the player may link to. */
    private inner class CasinoCycle(x: Int, y: Int) : AbstractButton(x, y, INNER_WIDTH, ROW, Component.empty()) {
        init {
            message = label()
        }

        private fun label(): Component = chosen?.let { Component.literal(it.name) } ?: Component.empty()

        override fun onPress() {
            val options = link.options
            if (options.size < 2) return
            chosen = options[(options.indexOf(chosen) + 1) % options.size]
            message = label()
        }

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val cycles = link.options.size > 1
            graphics.blitSprite(CasinoUi.SECONDARY.get(true, cycles && isHoveredOrFocused), x, y, width, height)
            graphics.blitSprite(CasinoUi.ACCOUNT_SHARED, x + 5, y + 5, 9, 9)
            graphics.drawString(font, message, x + 18, y + 5, CasinoUi.IVORY, true)
            val handle = Component.literal("#${chosen?.handle ?: ""}")
            graphics.drawString(font, handle, x + width - (if (cycles) 14 else 6) - font.width(handle), y + 5, CasinoUi.DIM, true)
            if (cycles) graphics.blitSprite(CasinoUi.PAGE_FORWARD, x + width - 9, y + 4, 7, 11)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
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
}
