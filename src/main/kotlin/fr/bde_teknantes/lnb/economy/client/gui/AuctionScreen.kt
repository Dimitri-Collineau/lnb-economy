package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.client.ClientAuction
import fr.bde_teknantes.lnb.economy.client.ClientEconomy
import fr.bde_teknantes.lnb.economy.money.Amounts
import fr.bde_teknantes.lnb.economy.network.AuctionBuyPayload
import fr.bde_teknantes.lnb.economy.network.AuctionCancelPayload
import fr.bde_teknantes.lnb.economy.network.AuctionCollectPayload
import fr.bde_teknantes.lnb.economy.network.AuctionEntry
import fr.bde_teknantes.lnb.economy.network.AuctionRequestPayload
import fr.bde_teknantes.lnb.economy.network.AuctionSellPayload
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor
import java.time.Duration
import java.time.Instant

/**
 * The auction house, in three gold tabs: Browse (search, sort, pages, buy), My listings (pick a stack in the inventory
 * shown below the listings and sell it, or take a listing back) and To collect (items waiting on the site). Each tab has
 * its grid on the left and a felt panel on the right. Everything shown comes from [ClientAuction]; this screen asks the server for it and sends orders.
 */
class AuctionScreen : Screen(Component.translatable("gui.lnbeconomy.auction.title")) {
    private companion object {
        const val PANEL_WIDTH = 292
        const val PANEL_HEIGHT = 200
        const val TAB_HEIGHT = 16
        const val TAB_WIDTH = 90
        const val TAB_STEP = 92
        const val COLUMNS = 9
        const val ROWS = 5
        const val CELL = 18
        const val GRID_X = 10
        const val GRID_Y = 32
        const val GRID_WIDTH = COLUMNS * CELL
        const val UNDER_GRID = GRID_Y + ROWS * CELL + 4

        // My listings: two rows of listings, then the player's inventory to pick what to sell.
        const val MINE_GRID_Y = 24
        const val MINE_ROWS = 2
        const val INVENTORY_LABEL_Y = 64
        const val INVENTORY_Y = 74
        const val HOTBAR_Y = 132
        const val SIDE_X = 178
        const val SIDE_WIDTH = 104
        const val SIDE_INNER = SIDE_WIDTH - 12
        const val WHITE = 0xFFFFFFFF.toInt()
        const val ORANGE = 0xFFFFA060.toInt()
        val TABS = listOf(
            AuctionRequestPayload.BROWSE to "gui.lnbeconomy.auction.tab.browse",
            AuctionRequestPayload.MINE to "gui.lnbeconomy.auction.tab.mine",
            AuctionRequestPayload.MAILBOX to "gui.lnbeconomy.auction.tab.mailbox",
        )
    }

    private var tab = AuctionRequestPayload.BROWSE
    private var query = ""
    private var sort = AuctionRequestPayload.SORTS.first()

    /** Cursors of the pages before the current one, to go back. */
    private val previous = ArrayDeque<String?>()
    private var selected: String? = null
    private var shownRevision = -1
    private var shownAccounts = -1

    /** What was typed, kept when the screen rebuilds on new data. */
    private var countText: String? = null
    private var priceText = ""

    /** The inventory slot (0..35) of the stack to sell; the one in hand to begin with. */
    private var sellSlot = -1

    private var account: AccountSelector? = null
    private var count: EditBox? = null
    private var price: EditBox? = null
    private var panelX = 0
    private var panelY = 0

    private val language: String get() = minecraft?.options?.languageCode ?: "en_us"
    private val gridX: Int get() = panelX + GRID_X
    private val gridY: Int get() = panelY + if (tab == AuctionRequestPayload.MINE) MINE_GRID_Y else GRID_Y
    private val gridRows: Int get() = if (tab == AuctionRequestPayload.MINE) MINE_ROWS else ROWS
    private val sideX: Int get() = panelX + SIDE_X

    override fun init() {
        if (shownRevision < 0) request(null)
        shownRevision = ClientAuction.revision
        shownAccounts = ClientEconomy.revision
        panelX = (width - PANEL_WIDTH) / 2
        panelY = (height - PANEL_HEIGHT - TAB_HEIGHT) / 2 + TAB_HEIGHT
        count = null
        price = null
        TABS.forEachIndexed { i, (id, key) ->
            addRenderableWidget(TabButton(id, Component.translatable(key), panelX + 8 + i * TAB_STEP, panelY - TAB_HEIGHT))
        }
        when (tab) {
            AuctionRequestPayload.BROWSE -> initBrowse()
            AuctionRequestPayload.MINE -> initMine()
            else -> initMailbox()
        }
        addRenderableWidget(
            CasinoButton.secondary(CommonComponents.GUI_DONE) { onClose() }
                .bounds(panelX + GRID_X, panelY + PANEL_HEIGHT - 28, 70, 18).build(),
        )
    }

    private fun initBrowse() {
        val box = field(gridX + 5, panelY + 14, 136, query, 64) { query = it }
        box.setHint(Component.translatable("gui.lnbeconomy.auction.search").withColor(CasinoUi.HINT))
        box.setFilter { true }
        addRenderableWidget(SortCycle(gridX + 150, panelY + 10))
        addRenderableWidget(
            CasinoButton.primary(Component.translatable("gui.lnbeconomy.auction.go")) {
                previous.clear()
                request(null)
            }.bounds(gridX + 232, panelY + 10, 40, 16).build(),
        )

        val page = ClientAuction.browse
        val pagerY = panelY + UNDER_GRID
        addRenderableWidget(PageButton(gridX, pagerY, forward = false) { if (previous.isNotEmpty()) request(previous.removeLast()) })
            .active = previous.isNotEmpty()
        addRenderableWidget(PageButton(gridX + GRID_WIDTH - 18, pagerY, forward = true) {
            previous.addLast(page?.cursor)
            request(page?.nextCursor)
        }).active = page?.nextCursor != null

        val sideY = gridY
        account = accountPicker(sideX + 6, sideY + 70)
        val chosen = page?.entries?.firstOrNull { it.id == selected }
        addRenderableWidget(
            CasinoButton.primary(Component.translatable("gui.lnbeconomy.auction.buy", chosen?.let { Lnbit.compact(it.price, language) } ?: "-")) {
                val entry = chosen ?: return@primary
                PacketDistributor.sendToServer(AuctionBuyPayload(entry.id, chosenAccount()))
                selected = null
            }.bounds(sideX + 6, sideY + 90, SIDE_INNER, 16).build(),
        ).active = chosen != null && account != null
    }

    private fun initMine() {
        if (sellSlot < 0) sellSlot = minecraft?.player?.inventory?.selected ?: 0
        val toSell = stackToSell()
        val sideY = panelY + 10
        count = field(sideX + 10, sideY + 55, SIDE_INNER - 8, countText ?: if (toSell.isEmpty) "" else toSell.count.toString(), 2) { countText = it }
        price = field(sideX + 10, sideY + 83, SIDE_INNER - 22, priceText, 10) { priceText = it }
        account = accountPicker(sideX + 6, sideY + 98)
        addRenderableWidget(
            CasinoButton.primary(Component.translatable("gui.lnbeconomy.auction.sell")) {
                val n = count?.value?.toIntOrNull() ?: return@primary
                val p = price?.value?.toLongOrNull() ?: return@primary
                PacketDistributor.sendToServer(AuctionSellPayload(sellSlot, n, p, chosenAccount()))
                countText = null
                priceText = ""
                request(null)
            }.bounds(sideX + 6, sideY + 116, SIDE_INNER, 16).build(),
        ).active = !toSell.isEmpty && account != null

        val chosen = ClientAuction.mine?.entries?.firstOrNull { it.id == selected }
        addRenderableWidget(
            CasinoButton.secondary(Component.translatable("gui.lnbeconomy.auction.take_back")) {
                val entry = chosen ?: return@secondary
                PacketDistributor.sendToServer(AuctionCancelPayload(entry.id))
                selected = null
                request(null)
            }.bounds(sideX, panelY + 156, SIDE_WIDTH, 16).build(),
        ).active = chosen != null
    }

    private fun stackToSell(): ItemStack = minecraft?.player?.inventory?.getItem(sellSlot) ?: ItemStack.EMPTY

    /** Where inventory slot [index] (0..35) sits on screen: the main inventory rows, then the hotbar. */
    private fun inventorySlotPos(index: Int): Pair<Int, Int> =
        if (index < 9) gridX + index * CELL to panelY + HOTBAR_Y
        else gridX + (index % 9) * CELL to panelY + INVENTORY_Y + (index / 9 - 1) * CELL

    private fun inventorySlotAt(mouseX: Double, mouseY: Double): Int? {
        if (tab != AuctionRequestPayload.MINE) return null
        return (0 until 36).firstOrNull { i ->
            val (x, y) = inventorySlotPos(i)
            mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL
        }
    }

    private fun initMailbox() {
        addRenderableWidget(
            CasinoButton.primary(Component.translatable("gui.lnbeconomy.auction.collect")) {
                PacketDistributor.sendToServer(AuctionCollectPayload)
                request(null)
            }.bounds(gridX, panelY + UNDER_GRID, GRID_WIDTH, 16).build(),
        ).active = !ClientAuction.mailbox?.entries.isNullOrEmpty()
    }

    /** A borderless box drawn on a sunken field sprite (see [renderBackground]). */
    private fun field(x: Int, y: Int, w: Int, value: String, maxLength: Int, onChange: (String) -> Unit): EditBox {
        val box = EditBox(font, x, y, w, 10, Component.empty())
        box.setBordered(false)
        box.setTextColor(CasinoUi.IVORY)
        box.setMaxLength(maxLength)
        box.setFilter { text -> text.all(Char::isDigit) }
        box.value = value
        box.setResponder(onChange)
        return addRenderableWidget(box)
    }

    /** The account that pays or sells: icon and balance only, the panel is narrow. */
    private fun accountPicker(x: Int, y: Int): AccountSelector? {
        val spendable = ClientEconomy.snapshot.accounts.filter { it.canSpend }
        val initial = ClientEconomy.snapshot.defaultAccount?.takeIf { it in spendable } ?: spendable.firstOrNull() ?: return null
        val selector = AccountSelector(x, y, SIDE_INNER, 16, spendable, initial, { Component.empty() })
        selector.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.lnbeconomy.auction.account")))
        return addRenderableWidget(selector)
    }

    /** The chosen account, or null for the default one (which the server assumes). */
    private fun chosenAccount(): String? = account?.value?.id?.takeIf { it != ClientEconomy.snapshot.defaultAccountId }

    private fun switchTo(id: Int) {
        tab = id
        selected = null
        countText = null
        previous.clear()
        request(null)
        rebuildWidgets()
    }

    private fun request(cursor: String?) {
        PacketDistributor.sendToServer(AuctionRequestPayload(tab, query.ifBlank { null }, sort, cursor))
    }

    override fun tick() {
        if (ClientAuction.revision != shownRevision || ClientEconomy.revision != shownAccounts) rebuildWidgets()
    }

    private fun entries(): List<AuctionEntry> = when (tab) {
        AuctionRequestPayload.BROWSE -> ClientAuction.browse?.entries
        AuctionRequestPayload.MINE -> ClientAuction.mine?.entries
        else -> ClientAuction.mailbox?.entries
    }.orEmpty()

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        inventorySlotAt(mouseX, mouseY)?.let {
            val stack = minecraft?.player?.inventory?.getItem(it) ?: ItemStack.EMPTY
            if (stack.isEmpty) return true
            sellSlot = it
            countText = stack.count.toString()
            rebuildWidgets()
            return true
        }
        entryAt(mouseX, mouseY)?.let {
            selected = it.id
            rebuildWidgets()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun entryAt(mouseX: Double, mouseY: Double): AuctionEntry? {
        if (mouseX < gridX || mouseY < gridY) return null
        val column = ((mouseX - gridX) / CELL).toInt()
        val row = ((mouseY - gridY) / CELL).toInt()
        if (column !in 0 until COLUMNS || row !in 0 until gridRows) return null
        return entries().getOrNull(row * COLUMNS + column)
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        // Tabs behind the window first, then the window, then the selected tab merged into it.
        TABS.forEachIndexed { i, (id, _) ->
            if (id != tab) graphics.blitSprite(CasinoUi.TAB, panelX + 8 + i * TAB_STEP, panelY - TAB_HEIGHT + 1, TAB_WIDTH, 18)
        }
        CasinoUi.panel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT)
        val active = TABS.indexOfFirst { it.first == tab }
        graphics.blitSprite(CasinoUi.TAB_SELECTED, panelX + 8 + active * TAB_STEP, panelY - TAB_HEIGHT - 1, TAB_WIDTH, 22)

        val list = entries()
        for (i in 0 until COLUMNS * gridRows) {
            val x = gridX + (i % COLUMNS) * CELL
            val y = gridY + (i / COLUMNS) * CELL
            val isSelected = list.getOrNull(i)?.id?.let { it == selected } == true
            if (isSelected) graphics.blitSprite(CasinoUi.SECONDARY.highlighted, x, y, CELL, CELL) else graphics.blitSprite(CasinoUi.SLOT, x, y, CELL, CELL)
        }
        if (tab == AuctionRequestPayload.MINE) {
            for (i in 0 until 36) {
                val (x, y) = inventorySlotPos(i)
                graphics.blitSprite(if (i == sellSlot) CasinoUi.SECONDARY.highlighted else CasinoUi.SLOT, x, y, CELL, CELL)
            }
        }
        when (tab) {
            AuctionRequestPayload.BROWSE -> {
                graphics.blitSprite(CasinoUi.TEXT_FIELD, gridX, panelY + 10, 146, 16)
                graphics.blitSprite(CasinoUi.PANEL_INSET, sideX, gridY, SIDE_WIDTH, 112)
                if (selectedEntry() != null) graphics.blitSprite(CasinoUi.TEXT_FIELD, sideX + 6, gridY + 6, 20, 20)
            }
            AuctionRequestPayload.MINE -> {
                val sideY = panelY + 10
                graphics.blitSprite(CasinoUi.PANEL_INSET, sideX, sideY, SIDE_WIDTH, 140)
                graphics.blitSprite(CasinoUi.TEXT_FIELD, sideX + 6, sideY + 16, 20, 20)
                count?.let { graphics.blitSprite(fieldSprite(it), sideX + 6, sideY + 52, SIDE_INNER, 14) }
                price?.let { graphics.blitSprite(fieldSprite(it), sideX + 6, sideY + 80, SIDE_INNER - 14, 14) }
            }
            else -> if (selectedEntry() != null) {
                graphics.blitSprite(CasinoUi.PANEL_INSET, sideX, gridY, SIDE_WIDTH, 60)
                graphics.blitSprite(CasinoUi.TEXT_FIELD, sideX + 6, gridY + 6, 20, 20)
            }
        }
    }

    private fun fieldSprite(box: EditBox): ResourceLocation = if (box.isFocused) CasinoUi.TEXT_FIELD_HIGHLIGHTED else CasinoUi.TEXT_FIELD

    private fun selectedEntry(): AuctionEntry? = entries().firstOrNull { it.id == selected }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val list = entries()
        for (i in 0 until COLUMNS * gridRows) {
            val entry = list.getOrNull(i) ?: continue
            val x = gridX + (i % COLUMNS) * CELL + 1
            val y = gridY + (i / COLUMNS) * CELL + 1
            if (entry.stack.isEmpty) {
                graphics.drawString(font, "?", x + 5, y + 4, 0xFFFF5555.toInt(), true)
            } else {
                graphics.renderItem(entry.stack, x, y)
                graphics.renderItemDecorations(font, entry.stack, x, y)
            }
        }
        if (list.isEmpty()) graphics.drawString(font, Component.translatable("gui.lnbeconomy.auction.empty"), gridX + 4, gridY + 5, CasinoUi.DIM, true)
        if (tab == AuctionRequestPayload.MINE) {
            val inventory = minecraft?.player?.inventory
            if (inventory != null) for (i in 0 until 36) {
                val (x, y) = inventorySlotPos(i)
                item(graphics, inventory.getItem(i), x + 1, y + 1)
            }
            graphics.drawString(font, Component.translatable("gui.lnbeconomy.auction.inventory"), gridX, panelY + INVENTORY_LABEL_Y, CasinoUi.DIM, true)
        }
        renderSide(graphics)
        entryAt(mouseX.toDouble(), mouseY.toDouble())?.let { graphics.renderComponentTooltip(font, tooltip(it), mouseX, mouseY) }
        inventorySlotAt(mouseX.toDouble(), mouseY.toDouble())?.let { slot ->
            val stack = minecraft?.player?.inventory?.getItem(slot) ?: ItemStack.EMPTY
            if (!stack.isEmpty) graphics.renderTooltip(font, stack, mouseX, mouseY)
        }
    }

    private fun renderSide(graphics: GuiGraphics) {
        when (tab) {
            AuctionRequestPayload.BROWSE -> {
                val pageLabel = Component.translatable("gui.lnbeconomy.auction.page", previous.size + 1)
                graphics.drawString(font, pageLabel, gridX + (GRID_WIDTH - font.width(pageLabel)) / 2, panelY + UNDER_GRID + 4, CasinoUi.DIM, true)
                val entry = selectedEntry()
                if (entry == null) {
                    wrapped(graphics, Component.translatable("gui.lnbeconomy.auction.pick"), sideX + 6, gridY + 6, CasinoUi.DIM)
                    return
                }
                item(graphics, entry.stack, sideX + 8, gridY + 8)
                graphics.drawString(font, fit("${entry.count} × ${entry.name}", 68), sideX + 30, gridY + 8, WHITE, true)
                graphics.drawString(font, fit(entry.detail, 68), sideX + 30, gridY + 18, CasinoUi.DIM, true)
                val total = Component.translatable("gui.lnbeconomy.auction.tooltip.price", Lnbit.compact(entry.price, language), entry.count)
                graphics.drawString(font, total, sideX + 6, gridY + 34, CasinoUi.GOLD_TEXT, true)
                if (entry.count > 1) {
                    val unit = Component.translatable("gui.lnbeconomy.auction.tooltip.unit", Lnbit.compact(entry.price / entry.count, language))
                    graphics.drawString(font, unit, sideX + 6, gridY + 45, CasinoUi.DIM, true)
                }
                graphics.drawString(font, Component.translatable("gui.lnbeconomy.auction.ends_short", timeLeft(entry.date)), sideX + 6, gridY + 56, CasinoUi.DIM, true)
            }
            AuctionRequestPayload.MINE -> {
                ClientAuction.mine?.let {
                    val limits = Component.translatable("gui.lnbeconomy.auction.limits", it.entries.size, it.max, it.taxPercent, it.days)
                    graphics.drawString(font, limits, gridX, panelY + 12, CasinoUi.DIM, true)
                }
                val sideY = panelY + 10
                graphics.drawString(font, Component.translatable("gui.lnbeconomy.auction.sell_title"), sideX + 6, sideY + 5, CasinoUi.GOLD_TEXT, true)
                val toSell = stackToSell()
                if (toSell.isEmpty) {
                    wrapped(graphics, Component.translatable("gui.lnbeconomy.auction.hand_empty"), sideX + 30, sideY + 17, CasinoUi.DIM, SIDE_WIDTH - 36)
                } else {
                    item(graphics, toSell, sideX + 8, sideY + 18)
                    graphics.drawString(font, Component.translatable("gui.lnbeconomy.auction.hand_label"), sideX + 30, sideY + 17, CasinoUi.DIM, true)
                    graphics.drawString(font, fit(toSell.hoverName.string, 68), sideX + 30, sideY + 27, WHITE, true)
                }
                graphics.drawString(font, Component.translatable("gui.lnbeconomy.auction.count"), sideX + 6, sideY + 42, CasinoUi.DIM, true)
                graphics.drawString(font, Component.translatable("gui.lnbeconomy.auction.price"), sideX + 6, sideY + 70, CasinoUi.DIM, true)
                graphics.drawString(font, Lnbit.symbol(), sideX + SIDE_WIDTH - 16, sideY + 82, CasinoUi.GOLD_TEXT, true)
            }
            else -> {
                graphics.drawString(font, Component.translatable("gui.lnbeconomy.auction.mailbox_help"), gridX, panelY + 14, CasinoUi.DIM, true)
                val entry = selectedEntry() ?: return
                item(graphics, entry.stack, sideX + 8, gridY + 8)
                graphics.drawString(font, fit(entry.name, 68), sideX + 30, gridY + 8, WHITE, true)
                graphics.drawString(font, Component.translatable("gui.lnbeconomy.auction.reason.${entry.detail}"), sideX + 6, gridY + 32, ORANGE, true)
            }
        }
    }

    private fun item(graphics: GuiGraphics, stack: ItemStack, x: Int, y: Int) {
        if (stack.isEmpty) return
        graphics.renderItem(stack, x, y)
        graphics.renderItemDecorations(font, stack, x, y)
    }

    /** [text] cut to [width] pixels, with an ellipsis when it was too long. */
    private fun fit(text: String, width: Int): String =
        if (font.width(text) <= width) text else font.plainSubstrByWidth(text, width - font.width("…")) + "…"

    private fun wrapped(graphics: GuiGraphics, text: Component, x: Int, y: Int, color: Int, width: Int = SIDE_INNER) {
        font.split(text, width).forEachIndexed { i, line -> graphics.drawString(font, line, x, y + i * 10, color, true) }
    }

    private fun tooltip(entry: AuctionEntry): List<Component> {
        val lines = mutableListOf<Component>()
        lines += if (entry.stack.isEmpty) Component.literal(entry.name).withStyle(ChatFormatting.RED) else entry.stack.hoverName
        when (tab) {
            AuctionRequestPayload.MAILBOX -> lines += Component.translatable("gui.lnbeconomy.auction.reason.${entry.detail}").withStyle(ChatFormatting.GRAY)
            else -> {
                lines += Component.translatable("gui.lnbeconomy.auction.tooltip.price", Amounts.format(entry.price, language), entry.count).withStyle(ChatFormatting.GOLD)
                if (entry.count > 1) {
                    lines += Component.translatable("gui.lnbeconomy.auction.tooltip.unit", Amounts.format(entry.price / entry.count, language)).withStyle(ChatFormatting.GRAY)
                }
                lines += Component.translatable("gui.lnbeconomy.auction.seller", entry.detail).withStyle(ChatFormatting.GRAY)
                lines += Component.translatable("gui.lnbeconomy.auction.tooltip.ends", timeLeft(entry.date)).withStyle(ChatFormatting.GRAY)
            }
        }
        if (entry.stack.isEmpty) lines += Component.translatable("gui.lnbeconomy.auction.unreadable").withStyle(ChatFormatting.RED)
        return lines
    }

    private fun timeLeft(iso: String): Component {
        val left = runCatching { Duration.between(Instant.now(), Instant.parse(iso)) }.getOrNull() ?: return Component.literal("?")
        val hours = left.toHours().coerceAtLeast(0)
        return if (hours >= 24) Component.translatable("gui.lnbeconomy.auction.days", hours / 24) else Component.translatable("gui.lnbeconomy.auction.hours", hours)
    }

    override fun isPauseScreen(): Boolean = false

    /** A tab: drawn by [renderBackground] (behind or merged with the window), so this only adds its label and click. */
    private inner class TabButton(private val id: Int, label: Component, x: Int, y: Int) : AbstractButton(x, y, TAB_WIDTH, TAB_HEIGHT, label) {
        override fun onPress() {
            if (tab != id) switchTo(id)
        }

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            if (tab == id) {
                CasinoUi.title(graphics, font, message, x + width / 2, y + 5)
            } else {
                val color = if (isHoveredOrFocused) CasinoUi.IVORY else CasinoUi.DIM
                graphics.drawString(font, message, x + (width - font.width(message)) / 2, y + 6, color, true)
            }
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }

    /** The sort order; a click moves to the next one. */
    private inner class SortCycle(x: Int, y: Int) : AbstractButton(x, y, 78, 16, Component.empty()) {
        init {
            message = label()
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.lnbeconomy.auction.sort")))
        }

        private fun label(): Component = Component.translatable("gui.lnbeconomy.auction.sort.$sort")

        override fun onPress() {
            val sorts = AuctionRequestPayload.SORTS
            sort = sorts[(sorts.indexOf(sort) + 1) % sorts.size]
            message = label()
        }

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            graphics.blitSprite(CasinoUi.SECONDARY.get(true, isHoveredOrFocused), x, y, width, height)
            graphics.drawString(font, fit(message.string, width - 16), x + 5, y + 4, CasinoUi.IVORY, true)
            graphics.blitSprite(CasinoUi.PAGE_FORWARD, x + width - 9, y + (height - 11) / 2, 7, 11)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }

    /** A page arrow in a felt button. */
    private inner class PageButton(x: Int, y: Int, private val forward: Boolean, private val press: () -> Unit) :
        AbstractButton(x, y, 18, 16, Component.literal(if (forward) ">" else "<")) {
        override fun onPress() = press()

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            graphics.blitSprite(CasinoUi.SECONDARY.get(active, isHoveredOrFocused), x, y, width, height)
            val arrow = when {
                forward && active -> CasinoUi.PAGE_FORWARD
                forward -> CasinoUi.PAGE_FORWARD_DISABLED
                active -> CasinoUi.PAGE_BACKWARD
                else -> CasinoUi.PAGE_BACKWARD_DISABLED
            }
            graphics.blitSprite(arrow, x + 6, y + 3, 7, 11)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }
}
