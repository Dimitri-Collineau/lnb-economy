package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.client.ClientAuction
import fr.bde_teknantes.lnb.economy.client.ClientEconomy
import fr.bde_teknantes.lnb.economy.money.Amounts
import fr.bde_teknantes.lnb.economy.network.AccountEntry
import fr.bde_teknantes.lnb.economy.network.AuctionBuyPayload
import fr.bde_teknantes.lnb.economy.network.AuctionCancelPayload
import fr.bde_teknantes.lnb.economy.network.AuctionCollectPayload
import fr.bde_teknantes.lnb.economy.network.AuctionEntry
import fr.bde_teknantes.lnb.economy.network.AuctionRequestPayload
import fr.bde_teknantes.lnb.economy.network.AuctionSellPayload
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor
import java.time.Duration
import java.time.Instant

/**
 * The auction house: Browse (search, sort, buy), My listings (list the stack in hand, take one back) and To collect
 * (items waiting on the site). Everything shown comes from [ClientAuction]; this screen asks the server for it and
 * sends orders. Its look is temporary.
 */
class AuctionScreen : Screen(Component.translatable("gui.lnbeconomy.auction.title")) {
    private companion object {
        const val WIDTH = 9 * 18 + 100
        const val COLUMNS = 9
        const val ROWS = 5
        const val CELL = 18
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

    private var account: CycleButton<AccountEntry>? = null
    private var search: EditBox? = null
    private var count: EditBox? = null
    private var price: EditBox? = null

    private val language: String get() = minecraft?.options?.languageCode ?: "en_us"
    private val left: Int get() = (width - WIDTH) / 2
    private val top: Int get() = height / 2 - 100
    private val gridTop: Int get() = top + 46

    override fun init() {
        if (shownRevision < 0) request(null)
        shownRevision = ClientAuction.revision
        shownAccounts = ClientEconomy.revision
        val tabs = listOf(
            AuctionRequestPayload.BROWSE to "gui.lnbeconomy.auction.tab.browse",
            AuctionRequestPayload.MINE to "gui.lnbeconomy.auction.tab.mine",
            AuctionRequestPayload.MAILBOX to "gui.lnbeconomy.auction.tab.mailbox",
        )
        tabs.forEachIndexed { i, (id, key) ->
            addRenderableWidget(Button.builder(Component.translatable(key)) { switchTo(id) }
                .bounds(left + i * (WIDTH / 3), top, WIDTH / 3 - 2, 18).build()).active = tab != id
        }
        when (tab) {
            AuctionRequestPayload.BROWSE -> initBrowse()
            AuctionRequestPayload.MINE -> initMine()
            else -> initMailbox()
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE) { onClose() }.bounds(width / 2 - 50, top + 200, 100, 18).build())
    }

    private fun initBrowse() {
        val box = EditBox(font, left, top + 22, WIDTH - 130, 18, Component.translatable("gui.lnbeconomy.auction.search"))
        box.value = query
        box.setResponder { query = it }
        box.setHint(Component.translatable("gui.lnbeconomy.auction.search").withStyle(ChatFormatting.DARK_GRAY))
        search = addRenderableWidget(box)
        addRenderableWidget(
            CycleButton.builder<String> { Component.translatable("gui.lnbeconomy.auction.sort.$it") }
                .withValues(AuctionRequestPayload.SORTS)
                .withInitialValue(sort)
                .displayOnlyValue()
                .create(left + WIDTH - 126, top + 22, 80, 18, Component.translatable("gui.lnbeconomy.auction.sort")) { _, value -> sort = value },
        )
        addRenderableWidget(Button.builder(Component.translatable("gui.lnbeconomy.auction.go")) {
            previous.clear()
            request(null)
        }.bounds(left + WIDTH - 42, top + 22, 42, 18).build())

        val page = ClientAuction.browse
        addRenderableWidget(Button.builder(Component.literal("<")) {
            if (previous.isNotEmpty()) request(previous.removeLast())
        }.bounds(left, gridTop + ROWS * CELL + 4, 20, 18).build()).active = previous.isNotEmpty()
        addRenderableWidget(Button.builder(Component.literal(">")) {
            previous.addLast(page?.cursor)
            request(page?.nextCursor)
        }.bounds(left + COLUMNS * CELL - 20, gridTop + ROWS * CELL + 4, 20, 18).build()).active = page?.nextCursor != null

        account = accountPicker(left + COLUMNS * CELL + 6, gridTop + 70)
        val chosen = page?.entries?.firstOrNull { it.id == selected }
        addRenderableWidget(Button.builder(Component.translatable("gui.lnbeconomy.auction.buy", chosen?.let { Amounts.format(it.price, language) } ?: "-")) {
            val entry = chosen ?: return@builder
            PacketDistributor.sendToServer(AuctionBuyPayload(entry.id, chosenAccount()))
            selected = null
        }.bounds(left + COLUMNS * CELL + 6, gridTop + 92, WIDTH - COLUMNS * CELL - 6, 18).build()).active = chosen != null && account != null
    }

    private fun initMine() {
        val hand = minecraft?.player?.mainHandItem ?: ItemStack.EMPTY
        val x = left + COLUMNS * CELL + 6
        val w = WIDTH - COLUMNS * CELL - 6
        count = addRenderableWidget(numberBox(x, gridTop + 22, w, countText ?: if (hand.isEmpty) "" else hand.count.toString(), 2) { countText = it })
        price = addRenderableWidget(numberBox(x, gridTop + 52, w, priceText, 10) { priceText = it })
        account = accountPicker(x, gridTop + 74)
        addRenderableWidget(Button.builder(Component.translatable("gui.lnbeconomy.auction.sell")) {
            val n = count?.value?.toIntOrNull() ?: return@builder
            val p = price?.value?.toLongOrNull() ?: return@builder
            PacketDistributor.sendToServer(AuctionSellPayload(n, p, chosenAccount()))
            countText = null
            priceText = ""
            request(null)
        }.bounds(x, gridTop + 96, w, 18).build()).active = !hand.isEmpty && account != null

        val chosen = ClientAuction.mine?.entries?.firstOrNull { it.id == selected }
        addRenderableWidget(Button.builder(Component.translatable("gui.lnbeconomy.auction.take_back")) {
            val entry = chosen ?: return@builder
            PacketDistributor.sendToServer(AuctionCancelPayload(entry.id))
            selected = null
            request(null)
        }.bounds(left, gridTop + ROWS * CELL + 4, COLUMNS * CELL, 18).build()).active = chosen != null
    }

    private fun initMailbox() {
        addRenderableWidget(Button.builder(Component.translatable("gui.lnbeconomy.auction.collect")) {
            PacketDistributor.sendToServer(AuctionCollectPayload)
            request(null)
        }.bounds(left, gridTop + ROWS * CELL + 4, COLUMNS * CELL, 18).build()).active = !ClientAuction.mailbox?.entries.isNullOrEmpty()
    }

    private fun numberBox(x: Int, y: Int, w: Int, value: String, maxLength: Int, onChange: (String) -> Unit): EditBox {
        val box = EditBox(font, x, y, w, 16, Component.empty())
        box.setMaxLength(maxLength)
        box.setFilter { text -> text.all(Char::isDigit) }
        box.value = value
        box.setResponder(onChange)
        return box
    }

    private fun accountPicker(x: Int, y: Int): CycleButton<AccountEntry>? {
        val spendable = ClientEconomy.snapshot.accounts.filter { it.canSpend }
        val initial = ClientEconomy.snapshot.defaultAccount?.takeIf { it in spendable } ?: spendable.firstOrNull() ?: return null
        return addRenderableWidget(
            CycleButton.builder<AccountEntry> { accountLabel(it) }
                .withValues(spendable)
                .withInitialValue(initial)
                .displayOnlyValue()
                .create(x, y, WIDTH - COLUMNS * CELL - 6, 18, Component.translatable("gui.lnbeconomy.auction.account")),
        )
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
        entryAt(mouseX, mouseY)?.let {
            selected = it.id
            rebuildWidgets()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun entryAt(mouseX: Double, mouseY: Double): AuctionEntry? {
        val column = ((mouseX - left) / CELL).toInt()
        val row = ((mouseY - gridTop) / CELL).toInt()
        if (mouseX < left || mouseY < gridTop || column !in 0 until COLUMNS || row !in 0 until ROWS) return null
        return entries().getOrNull(row * COLUMNS + column)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        graphics.drawCenteredString(font, title, width / 2, top - 14, 0xFFFFFF)
        val list = entries()
        for (i in 0 until COLUMNS * ROWS) {
            val x = left + (i % COLUMNS) * CELL
            val y = gridTop + (i / COLUMNS) * CELL
            val entry = list.getOrNull(i)
            graphics.fill(x, y, x + CELL - 1, y + CELL - 1, if (entry != null && entry.id == selected) 0xFF5A7A3A.toInt() else 0xFF373737.toInt())
            if (entry == null) continue
            if (entry.stack.isEmpty) {
                graphics.drawString(font, "?", x + 6, y + 5, 0xFF5555)
            } else {
                graphics.renderItem(entry.stack, x + 1, y + 1)
                graphics.renderItemDecorations(font, entry.stack, x + 1, y + 1)
            }
        }
        if (list.isEmpty()) graphics.drawString(font, Component.translatable("gui.lnbeconomy.auction.empty"), left + 4, gridTop + 4, 0xA0A0A0)
        renderSide(graphics)
        entryAt(mouseX.toDouble(), mouseY.toDouble())?.let { graphics.renderComponentTooltip(font, tooltip(it), mouseX, mouseY) }
    }

    private fun renderSide(graphics: GuiGraphics) {
        val x = left + COLUMNS * CELL + 6
        when (tab) {
            AuctionRequestPayload.BROWSE -> {
                val entry = ClientAuction.browse?.entries?.firstOrNull { it.id == selected }
                if (entry != null) {
                    graphics.drawString(font, "${entry.count} × ${entry.name}", x, gridTop, 0xFFFFFF)
                    graphics.drawString(font, Component.translatable("gui.lnbeconomy.auction.seller", entry.detail), x, gridTop + 12, 0xA0A0A0)
                } else {
                    graphics.drawString(font, Component.translatable("gui.lnbeconomy.auction.pick"), x, gridTop, 0xA0A0A0)
                }
            }
            AuctionRequestPayload.MINE -> {
                val hand = minecraft?.player?.mainHandItem ?: ItemStack.EMPTY
                val label = if (hand.isEmpty) Component.translatable("gui.lnbeconomy.auction.hand_empty") else Component.translatable("gui.lnbeconomy.auction.hand", hand.hoverName)
                graphics.drawString(font, label, x, gridTop, 0xFFFFFF)
                graphics.drawString(font, Component.translatable("gui.lnbeconomy.auction.count"), x, gridTop + 12, 0xA0A0A0)
                graphics.drawString(font, Component.translatable("gui.lnbeconomy.auction.price"), x, gridTop + 42, 0xA0A0A0)
                ClientAuction.mine?.let {
                    graphics.drawString(font, Component.translatable("gui.lnbeconomy.auction.limits", it.entries.size, it.max, it.taxPercent, it.days), left, top + 26, 0xA0A0A0)
                }
            }
            else -> graphics.drawString(font, Component.translatable("gui.lnbeconomy.auction.mailbox_help"), left, top + 26, 0xA0A0A0)
        }
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

    private fun accountLabel(account: AccountEntry): Component {
        val name = if (account.isPersonal) Component.translatable("gui.lnbeconomy.accounts.personal").string else "#${account.handle}"
        return Component.literal("$name  ${Amounts.compact(account.balance, language)}")
    }
}
