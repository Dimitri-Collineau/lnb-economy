package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.client.ClientEconomy
import fr.bde_teknantes.lnb.economy.client.ClientMarket
import fr.bde_teknantes.lnb.economy.client.ClientMarket.Entry
import fr.bde_teknantes.lnb.economy.market.MarketPricing
import fr.bde_teknantes.lnb.economy.money.Amounts
import fr.bde_teknantes.lnb.economy.network.AccountEntry
import fr.bde_teknantes.lnb.economy.network.MarketOrderPayload
import fr.bde_teknantes.lnb.economy.network.MarketRequestPayload
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Market, in the B2 look: a list of what can be sold (or bought) with its price for 1 and for 64 and how far it is
 * from its base price, and an order panel for the item clicked. Every price is computed here from the catalog the
 * server sent; the server checks and relays the order. Nothing is ever sent to the site from the client.
 */
class MarketScreen : Screen(Component.translatable("gui.lnbeconomy.market.title")) {
    private companion object {
        const val W = 312
        const val H = 226
        const val ROWS = 6
        const val ROW_H = 20
        const val LIST_Y = 80
        const val PRICE_CHIPS = 3
        val PERIODS = MarketRequestPayload.PERIODS
        val CHIPS = listOf(1, 16, 64)
        val SORTS = listOf("name", "price", "change")
        val PERIOD_HOURS = listOf(24.0, 7 * 24.0, 30 * 24.0, 365 * 24.0)
    }

    private var selling = true
    private var query = ""
    private var sort = 0
    private var page = 0
    private var selected: Entry? = null
    private var quantity = 1
    private var quantityText = ""
    private var period = 0
    private var confirming = false
    private var account: AccountEntry? = null
    private var shownAccounts = -1
    private var x0 = 0
    private var y0 = 0
    private var search: EditBox? = null
    private var custom: EditBox? = null

    /** The quantity chips, each with the quantity it sets, so the one that matches what is typed stays lit. */
    private val chipButtons = ArrayList<Pair<() -> Int, MarketUi.Button>>()
    private val language: String get() = MarketUi.language()

    override fun init() {
        MarketUi.switching = false
        shownAccounts = ClientEconomy.revision
        x0 = (width - W) / 2
        y0 = (height - H) / 2
        search = null
        custom = null
        if (selected == null) initList() else initOrder()
        val footerY = y0 + H - 22
        if (selected == null) {
            addRenderableWidget(MarketUi.Button(x0 + 8, footerY, 70, 16, CommonComponents.GUI_DONE, MarketUi.Button.Kind.NORMAL) { onClose() })
            addRenderableWidget(MarketUi.Button(x0 + W - 8 - 20, footerY, 20, 16, Component.literal(">"), MarketUi.Button.Kind.NORMAL) { turn(1) })
            addRenderableWidget(MarketUi.Button(x0 + W - 8 - 68, footerY, 20, 16, Component.literal("<"), MarketUi.Button.Kind.NORMAL) { turn(-1) })
        } else {
            addRenderableWidget(MarketUi.Button(x0 + 8, footerY, 70, 16, CommonComponents.GUI_BACK, MarketUi.Button.Kind.NORMAL) { select(null) })
        }
    }

    // ---- the list

    private var listKey: List<Any> = emptyList()
    private var listCache: List<Entry> = emptyList()

    /**
     * What the list shows, sorted. Computed again only when the inputs change or every few seconds, never every frame:
     * prices drift all the time, and sorting on them live would shuffle rows that are nearly equal. Ties are broken by
     * name, and the change is compared as the whole percent that is shown.
     */
    private fun visible(): List<Entry> {
        val key = listOf(selling, query, sort, ClientMarket.revision, System.currentTimeMillis() / 5_000)
        if (key == listKey) return listCache
        val needle = query.trim().lowercase()
        val list = ClientMarket.entries.filter {
            (if (selling) it.data.sellable else it.data.buyable) && (needle.isEmpty() || it.name.lowercase().contains(needle) || it.id.contains(needle))
        }
        val byName = compareBy<Entry> { it.name.lowercase() }.thenBy { it.id }
        listCache = when (SORTS[sort]) {
            "price" -> list.sortedWith(compareByDescending<Entry> { ClientMarket.total(it, 1, selling) ?: 0 }.then(byName))
            "change" -> list.sortedWith(compareByDescending<Entry> { (ClientMarket.change(it) * 100).roundToInt() }.then(byName))
            else -> list.sortedWith(byName)
        }
        listKey = key
        return listCache
    }

    private fun pages(count: Int) = maxOf(1, (count + ROWS - 1) / ROWS)

    private fun turn(by: Int) {
        page = (page + by).coerceIn(0, pages(visible().size) - 1)
    }

    private fun initList() {
        addRenderableWidget(MarketUi.Button(x0 + 8, y0 + 24, 60, 16, Component.translatable("gui.lnbeconomy.market.sell"), if (selling) MarketUi.Button.Kind.TAB_ON else MarketUi.Button.Kind.TAB) {
            selling = true; page = 0; rebuildWidgets()
        }.also { it.active = ClientMarket.sell })
        addRenderableWidget(MarketUi.Button(x0 + 70, y0 + 24, 60, 16, Component.translatable("gui.lnbeconomy.market.buy"), if (!selling) MarketUi.Button.Kind.TAB_ON else MarketUi.Button.Kind.TAB) {
            selling = false; page = 0; rebuildWidgets()
        }.also { it.active = ClientMarket.buy })
        addRenderableWidget(MarketUi.Button(x0 + W - 8 - 96, y0 + 24, 96, 16, Component.translatable("gui.lnbeconomy.market.efficiency"), MarketUi.Button.Kind.NORMAL) {
            MarketUi.go(MarketEfficiencyScreen(this))
        })
        val box = EditBox(font, x0 + 12, y0 + 49, 150, 10, Component.translatable("gui.lnbeconomy.market.search"))
        box.setBordered(false)
        box.setTextColor(PixelBox.TEXT)
        box.setHint(Component.translatable("gui.lnbeconomy.market.search").withColor(PixelBox.MUTED))
        box.value = query
        box.setResponder { query = it; page = 0 }
        search = box
        addRenderableWidget(box)
        addRenderableWidget(MarketUi.Button(x0 + W - 8 - 110, y0 + 44, 110, 18, sortLabel(), MarketUi.Button.Kind.NORMAL) {
            sort = (sort + 1) % SORTS.size; page = 0; rebuildWidgets()
        })
    }

    private fun sortLabel() = Component.translatable("gui.lnbeconomy.market.sort.${SORTS[sort]}")

    private fun heldOf(entry: Entry): Int {
        val player = minecraft?.player ?: return 0
        return player.inventory.items.filter { it.`is`(entry.item) && it.componentsPatch.isEmpty }.sumOf { it.count }
    }

    private fun coins(cents: Long): String = Amounts.format(cents, language)

    private fun percent(change: Double): String = (if (change > 0.0005) "+" else if (change < -0.0005) "-" else "") + "${(abs(change) * 100).roundToInt()} %"

    private fun renderList(g: GuiGraphics, mouseX: Int, mouseY: Int) {
        PixelBox.inset(g, x0 + 8, y0 + 44, 160, 18)
        val list = visible()
        page = page.coerceIn(0, pages(list.size) - 1)
        g.drawString(font, Component.translatable("gui.lnbeconomy.market.col.item"), x0 + 34, y0 + 69, PixelBox.MUTED, false)
        val right = x0 + W - 12
        for ((label, edge) in listOf("gui.lnbeconomy.market.col.one" to right - 104, "gui.lnbeconomy.market.col.stack" to right - 54, "gui.lnbeconomy.market.col.change" to right)) {
            val t = Component.translatable(label)
            g.drawString(font, t, edge - font.width(t), y0 + 69, PixelBox.MUTED, false)
        }
        var tooltip: List<Component>? = null
        for (i in 0 until ROWS) {
            val entry = list.getOrNull(page * ROWS + i) ?: break
            val ry = y0 + LIST_Y + i * ROW_H
            val hover = mouseX in x0 + 8 until x0 + W - 8 && mouseY in ry until ry + ROW_H - 2
            PixelBox.flat(g, x0 + 8, ry, W - 16, ROW_H - 2, if (hover) PixelBox.RAISED else PixelBox.SURFACE)
            g.renderItem(entry.stack, x0 + 12, ry + 1)
            val held = if (selling) heldOf(entry) else 0
            val nameRoom = right - 112 - (x0 + 34)
            g.drawString(font, CasinoUi.fit(font, entry.name, nameRoom), x0 + 34, ry + 5, if (selling && held == 0) PixelBox.MUTED else PixelBox.TEXT, false)
            val one = ClientMarket.total(entry, 1, selling)
            val stack = ClientMarket.total(entry, 64, selling)
            val y = ry + 5
            one?.let { coins(it) }?.let { g.drawString(font, it, right - 104 - font.width(it), y, PixelBox.TEXT, false) }
            stack?.let { coins(it) }?.let { g.drawString(font, it, right - 54 - font.width(it), y, PixelBox.MUTED, false) }
            val change = ClientMarket.change(entry)
            val color = if (change > 0.0005) PixelBox.GREEN else if (change < -0.0005) PixelBox.RED else PixelBox.MUTED
            val badge = percent(change)
            g.drawString(font, badge, right - font.width(badge), y, color, false)
            if (hover) tooltip = rowTooltip(entry, held)
        }
        if (list.isEmpty()) {
            g.drawString(font, Component.translatable("gui.lnbeconomy.market.none"), x0 + 14, y0 + LIST_Y + 6, PixelBox.MUTED, false)
        }
        val label = "${page + 1} / ${pages(list.size)}"
        g.drawString(font, label, x0 + W - 42 - font.width(label) / 2, y0 + H - 18, PixelBox.MUTED, false)
        tooltip?.let { g.renderComponentTooltip(font, it, mouseX, mouseY) }
    }

    /** The exact payout (or cost) of the stack under the cursor, the average per item and the rate. */
    private fun rowTooltip(entry: Entry, held: Int): List<Component> {
        val lines = ArrayList<Component>()
        lines += entry.stack.hoverName
        val max = ClientMarket.maxOrderItems.coerceAtLeast(1)
        if (selling) {
            val n = (if (held > 0) held else entry.stack.maxStackSize).coerceIn(1, max)
            val sale = ClientMarket.sellQuote(entry, n)
            lines += Component.translatable(if (held > 0) "gui.lnbeconomy.market.tip.sell_held" else "gui.lnbeconomy.market.tip.sell_stack", n, coins(sale.totalCents))
            lines += Component.translatable("gui.lnbeconomy.market.tip.average", coins((sale.totalCents.toDouble() / n).roundToInt().toLong()), rateText(sale))
        } else {
            val n = entry.stack.maxStackSize.coerceIn(1, max)
            val purchase = ClientMarket.buyQuote(entry, n)
            lines += Component.translatable("gui.lnbeconomy.market.tip.buy_stack", n, coins(purchase.totalCents))
            lines += Component.translatable("gui.lnbeconomy.market.tip.average", coins((purchase.totalCents.toDouble() / n).roundToInt().toLong()), "")
        }
        return lines
    }

    private fun rateText(sale: MarketPricing.Sale): String =
        sale.brackets.map { "${(it.rate * 100).roundToInt()} %" }.distinct().joinToString(" > ")

    // ---- the order panel

    private fun select(entry: Entry?) {
        selected = entry
        quantity = 1
        quantityText = ""
        confirming = false
        account = null
        if (entry != null) ClientMarket.requestHistory(entry.id, PERIODS[period])
        rebuildWidgets()
    }

    private fun maxQuantity(entry: Entry): Int =
        if (selling) minOf(heldOf(entry), ClientMarket.maxOrderItems) else ClientMarket.maxOrderItems

    private fun setQuantity(value: Int) {
        quantity = value.coerceAtLeast(0)
        confirming = false
        custom?.let { box -> val text = if (quantity > 0) quantity.toString() else ""; if (box.value != text) { quantityText = text; box.value = text } }
    }

    /** Accounts the player can pay with (buying) or be paid on (selling, when the site allows it). */
    private fun choices(): List<AccountEntry> {
        val all = ClientEconomy.snapshot.accounts
        return if (selling) {
            if (ClientMarket.saleAccount) all.filter { it.role != "viewer" && !it.frozen } else all.filter { it.isPersonal }
        } else all.filter { it.canSpend }
    }

    private fun currentAccount(): AccountEntry? {
        val list = choices()
        account?.let { chosen -> list.firstOrNull { it.id == chosen.id }?.let { return it } }
        val default = ClientEconomy.snapshot.defaultAccountId
        return (if (selling) list.firstOrNull { it.isPersonal } else list.firstOrNull { it.id == default }) ?: list.firstOrNull()
    }

    /** Top of the order panel's content: it starts right under the title, where the tabs are on the list. */
    private val oy: Int get() = y0 + 30

    private fun initOrder() {
        val entry = selected ?: return
        val left = x0 + 12
        PERIODS.forEachIndexed { i, p ->
            addRenderableWidget(MarketUi.Button(left + i * 38, oy + 67, 36, 12, Component.translatable("gui.lnbeconomy.market.period.$p"), if (i == period) MarketUi.Button.Kind.CHIP_ON else MarketUi.Button.Kind.CHIP) {
                period = i; ClientMarket.requestHistory(entry.id, p); rebuildWidgets()
            })
        }
        val max = maxQuantity(entry)
        chipButtons.clear()
        CHIPS.forEachIndexed { i, n ->
            val chip = MarketUi.Button(left + i * 32, oy + 118, 30, 14, Component.literal(n.toString()), if (quantity == n) MarketUi.Button.Kind.CHIP_ON else MarketUi.Button.Kind.CHIP) { setQuantity(n) }
                .also { it.active = n <= max }
            chipButtons.add(Pair({ n }, chip))
            addRenderableWidget(chip)
        }
        if (selling) {
            // "All" is capped by the limit; the label says so when the limit is lower than what is held.
            val all = heldOf(entry)
            val label = if (all > ClientMarket.maxOrderItems) Component.translatable("gui.lnbeconomy.market.all_limit", ClientMarket.maxOrderItems) else Component.translatable("gui.lnbeconomy.market.all", all)
            val chip = MarketUi.Button(left + 96, oy + 118, 54, 14, label, MarketUi.Button.Kind.CHIP) { setQuantity(max) }.also { it.active = max > 0 }
            chipButtons.add(Pair({ max }, chip))
            addRenderableWidget(chip)
        }
        val box = EditBox(font, left + 4, oy + 139, 142, 10, Component.translatable("gui.lnbeconomy.market.custom"))
        box.setBordered(false)
        box.setTextColor(PixelBox.TEXT)
        box.setMaxLength(5)
        box.setFilter { text -> text.all { it.isDigit() } }
        box.setHint(Component.translatable("gui.lnbeconomy.market.custom").withColor(PixelBox.MUTED))
        box.value = quantityText
        box.setResponder { text -> quantityText = text; quantity = text.toIntOrNull() ?: 0; confirming = false }
        custom = box
        addRenderableWidget(box)
        val choices = choices()
        val pick = currentAccount()
        val label = pick?.let { AccountSelector.name(it) } ?: Component.translatable("gui.lnbeconomy.accounts.personal")
        addRenderableWidget(MarketUi.Button(x0 + 172, oy + 62, 128, 14, label, MarketUi.Button.Kind.NORMAL) {
            account = choices.getOrNull((choices.indexOfFirst { it.id == pick?.id } + 1) % choices.size)
            rebuildWidgets()
        }.also { it.active = choices.size > 1 })
        val actionLabel = when {
            confirming -> Component.translatable("gui.lnbeconomy.market.confirm")
            selling -> Component.translatable("gui.lnbeconomy.market.do_sell")
            else -> Component.translatable("gui.lnbeconomy.market.do_buy")
        }
        // Level with the custom amount field, so the bottom of both columns lines up.
        addRenderableWidget(MarketUi.Button(x0 + 172, oy + 134, 128, 18, actionLabel, MarketUi.Button.Kind.ACTION) { submit(entry) }.also { it.active = ready(entry) })
    }

    private class Priced(val cents: Long, val perItem: Double, val rate: String, val sale: MarketPricing.Sale?)

    private fun priced(entry: Entry): Priced? {
        if (quantity < 1) return null
        return if (selling) {
            val sale = ClientMarket.sellQuote(entry, quantity)
            Priced(sale.totalCents, sale.totalCents / 100.0 / quantity, rateText(sale), sale)
        } else {
            val purchase = ClientMarket.buyQuote(entry, quantity)
            Priced(purchase.totalCents, purchase.perItem, "", null)
        }
    }

    private fun ready(entry: Entry): Boolean {
        val max = maxQuantity(entry)
        if (quantity !in 1..maxOf(max, 0)) return false
        val price = priced(entry) ?: return false
        if (selling && price.cents <= 0) return false
        val pay = currentAccount() ?: return false
        return selling || pay.balance >= price.cents
    }

    private fun submit(entry: Entry) {
        val price = priced(entry) ?: return
        if (!confirming && price.cents > ClientMarket.confirmAbove && ClientMarket.confirmAbove > 0) {
            confirming = true
            rebuildWidgets()
            return
        }
        val pay = currentAccount()
        val id = if (selling) pay?.takeIf { !it.isPersonal }?.id else pay?.id
        PacketDistributor.sendToServer(MarketOrderPayload(selling, entry.id, quantity, price.cents, id))
        select(null)
    }

    private fun renderOrder(g: GuiGraphics, mouseX: Int, mouseY: Int) {
        val entry = selected ?: return
        val left = x0 + 12
        PixelBox.inset(g, x0 + 8, y0 + 26, W - 16, 166, PixelBox.SURFACE)
        g.renderItem(entry.stack, left, oy)
        g.drawString(font, CasinoUi.fit(font, entry.name, 130), left + 22, oy + 4, PixelBox.TEXT, false)
        // History chart, with the value under the cursor.
        PixelBox.inset(g, left, oy + 20, 150, 44)
        drawChart(g, left + 2, oy + 22, 146, 40, ClientMarket.history(entry.id, PERIODS[period]), mouseX, mouseY)
        val ref = ClientMarket.reference(entry)
        val now = Component.empty().append(Lnbit.symbol()).append(" " + Amounts.format((ref * 100).roundToInt().toLong(), language) + " (" + percent(ClientMarket.change(entry)) + ")")
        val rows = listOf(
            "gui.lnbeconomy.market.price_now" to now,
            "gui.lnbeconomy.market.tier_row" to Component.literal(entry.data.tier.toString()),
            "gui.lnbeconomy.market.hold" to Component.literal(heldOf(entry).toString()),
        )
        rows.forEachIndexed { i, (key, value) ->
            val y = oy + 84 + i * 10
            g.drawString(font, Component.translatable(key), left, y, PixelBox.MUTED, false)
            g.drawString(font, value, left + 150 - font.width(value), y, PixelBox.TEXT, false)
        }
        PixelBox.inset(g, left, oy + 136, 150, 16)
        // Right column.
        val rx = x0 + 172
        val price = priced(entry)
        g.drawString(font, Component.translatable(if (selling) "gui.lnbeconomy.market.you_get" else "gui.lnbeconomy.market.cost"), rx, oy, PixelBox.MUTED, false)
        g.drawString(font, if (price != null) coins(price.cents) else "-", rx, oy + 11, PixelBox.GOLD, false)
        price?.let {
            g.drawString(font, Component.translatable("gui.lnbeconomy.market.per_item", coins((it.perItem * 100).roundToInt().toLong())), rx, oy + 26, PixelBox.TEXT, false)
            if (it.rate.isNotEmpty()) g.drawString(font, Component.translatable("gui.lnbeconomy.market.rate", it.rate), rx, oy + 37, PixelBox.TEXT, false)
        }
        g.drawString(font, Component.translatable("gui.lnbeconomy.market.pay_to"), rx, oy + 52, PixelBox.MUTED, false)
        val pay = currentAccount()
        if (pay != null && price != null) {
            val after = pay.balance + if (selling) price.cents else -price.cents
            g.drawString(font, Component.translatable("gui.lnbeconomy.market.after", coins(after)), rx, oy + 82, if (after < 0) PixelBox.RED else PixelBox.TEXT, false)
        }
        // A gold line only when the sale crosses a bracket, or is capped; a red one for what blocks the order.
        noteFor(entry, price)?.let { (text, color) -> CasinoUi.wrapped(g, font, text, rx, oy + 96, 128, 4, color, 9) }
    }

    private fun noteFor(entry: Entry, price: Priced?): Pair<Component, Int>? {
        val max = maxQuantity(entry)
        if (quantity > max && max >= 0) {
            return if (selling && heldOf(entry) < quantity) Component.translatable("gui.lnbeconomy.market.only_hold", heldOf(entry)) to PixelBox.RED
            else Component.translatable("gui.lnbeconomy.market.limit", ClientMarket.maxOrderItems) to PixelBox.RED
        }
        if (price == null) return null
        if (selling && price.cents <= 0) return Component.translatable("economy.lnbeconomy.problem.zeroPayout") to PixelBox.RED
        if (!selling && currentAccount()?.let { it.balance < price.cents } == true) return Component.translatable("economy.lnbeconomy.problem.insufficientFunds") to PixelBox.RED
        val sale = price.sale
        if (sale != null) {
            if (sale.capped || ClientMarket.capped) return Component.translatable("economy.lnbeconomy.market.capped") to PixelBox.GOLD
            val rates = sale.brackets.map { (it.rate * 100).roundToInt() }.distinct()
            if (rates.size > 1) return Component.translatable("gui.lnbeconomy.market.crosses", entry.data.tier, rates.first(), rates.last()) to PixelBox.GOLD
        }
        return null
    }

    private fun drawChart(g: GuiGraphics, x: Int, y: Int, w: Int, h: Int, prices: List<Float>?, mouseX: Int, mouseY: Int) {
        if (prices == null || prices.size < 2) {
            val t = Component.translatable(if (prices == null) "gui.lnbeconomy.market.loading" else "gui.lnbeconomy.market.no_history")
            g.drawString(font, t, x + (w - font.width(t)) / 2, y + h / 2 - 4, PixelBox.MUTED, false)
            return
        }
        val lo = prices.min()
        val hi = prices.max().let { if (it - lo < 1e-9f) lo + 1f else it }
        for (px in 0 until w) {
            val value = prices[(px.toLong() * (prices.size - 1) / (w - 1)).toInt()]
            val top = y + h - 1 - ((value - lo) / (hi - lo) * (h - 2)).toInt()
            g.fill(x + px, top, x + px + 1, y + h, 0x33A99BFF)
            g.fill(x + px, top, x + px + 1, top + 1, PixelBox.DUSK)
        }
        if (mouseX !in x until x + w || mouseY !in y until y + h) return
        // A cursor line and a bubble: the price at that point and how long ago it was.
        val px = mouseX - x
        val index = (px.toLong() * (prices.size - 1) / (w - 1)).toInt()
        val value = prices[index]
        g.fill(mouseX, y, mouseX + 1, y + h, 0x88FFFFFF.toInt())
        val dotY = y + h - 1 - ((value - lo) / (hi - lo) * (h - 2)).toInt()
        g.fill(mouseX - 1, dotY - 1, mouseX + 2, dotY + 2, PixelBox.TEXT)
        val share = 1.0 - index.toDouble() / (prices.size - 1)
        val hours = PERIOD_HOURS[period] * share
        val ago = when {
            share < 0.005 -> Component.translatable("gui.lnbeconomy.market.now")
            hours < 48 -> Component.translatable("gui.lnbeconomy.market.ago_hours", hours.roundToInt().coerceAtLeast(1))
            else -> Component.translatable("gui.lnbeconomy.market.ago_days", (hours / 24).roundToInt())
        }
        val price = Component.empty().append(Lnbit.symbol()).append(" " + Amounts.format((value * 100).roundToInt().toLong(), language))
        g.renderComponentTooltip(font, listOf(price, ago.copy().withColor(PixelBox.MUTED)), mouseX, mouseY)
    }

    // ---- the screen

    override fun tick() {
        for ((value, chip) in chipButtons) {
            chip.kindNow = if (quantity > 0 && quantity == value()) MarketUi.Button.Kind.CHIP_ON else MarketUi.Button.Kind.CHIP
        }
        if (ClientEconomy.revision != shownAccounts && selected != null) rebuildWidgets()
        shownAccounts = ClientEconomy.revision
    }

    /** Drawn here, not in render: the game blurs what a screen drew before its background step. */
    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderTransparentBackground(graphics)
        PixelBox.raised(graphics, x0, y0, W, H, PixelBox.BG)
        graphics.drawString(font, title, x0 + 10, y0 + 9, PixelBox.TEXT, false)
        val balance = ClientEconomy.snapshot.accounts.firstOrNull { it.id == ClientEconomy.snapshot.defaultAccountId }?.balance
        balance?.let {
            val text = Component.empty().append(Lnbit.symbol()).append(" " + coins(it))
            graphics.drawString(font, text, x0 + W - 10 - font.width(text), y0 + 9, PixelBox.GOLD, false)
        }
        if (selected == null) renderList(graphics, mouseX, mouseY) else renderOrder(graphics, mouseX, mouseY)
        val toast = ClientMarket.toast
        if (toast != null && System.currentTimeMillis() < ClientMarket.toastUntil) {
            // Two lines between Done and the page buttons, so a whole sentence fits.
            CasinoUi.wrapped(graphics, font, toast.second, x0 + 84, y0 + H - 24, W - 84 - 84, 2, if (toast.first) PixelBox.GREEN else PixelBox.RED, 9)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (selected == null && button == 0) {
            val list = visible()
            for (i in 0 until ROWS) {
                val entry = list.getOrNull(page * ROWS + i) ?: break
                val ry = y0 + LIST_Y + i * ROW_H
                if (mouseX >= x0 + 8 && mouseX < x0 + W - 8 && mouseY >= ry && mouseY < ry + ROW_H - 2) {
                    select(entry)
                    return true
                }
            }
        }
        val handled = super.mouseClicked(mouseX, mouseY, button)
        // A click anywhere else lets go of a text field, so the keys work again.
        for (box in listOf(search, custom)) {
            if (box != null && box.isFocused && !box.isMouseOver(mouseX, mouseY)) {
                box.isFocused = false
                if (focused === box) focused = null
            }
        }
        return handled
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // Escape or Enter leaves a focused field first; only then does Escape close the panel.
        val field = listOf(search, custom).firstOrNull { it != null && it.isFocused }
        if (field != null && (keyCode == 256 || keyCode == 257 || keyCode == 335)) {
            field.isFocused = false
            if (focused === field) focused = null
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (selected == null) {
            turn(if (scrollY < 0) 1 else -1)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun onClose() {
        if (selected != null) select(null) else super.onClose()
    }

    override fun removed() = MarketUi.left()

    override fun isPauseScreen() = false
}
