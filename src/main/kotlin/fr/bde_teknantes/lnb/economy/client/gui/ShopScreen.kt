package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.client.ClientEconomy
import fr.bde_teknantes.lnb.economy.network.OpenShopPayload
import fr.bde_teknantes.lnb.economy.network.ShopOrderPayload
import fr.bde_teknantes.lnb.economy.shop.ShopQuote
import fr.bde_teknantes.lnb.economy.shop.Stock
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

/**
 * A shop as a customer sees it. One that sells shows its item, big, with its price per lot, stock and buyback, then the
 * account, the number of lots and "Buy" (and "Sell" when it buys back). One that only buys shows what it pays per lot,
 * how many lots it still takes and how many the customer carries, and its only button sells. The numbers come from
 * [ShopQuote]; this screen only draws them and sends the order.
 */
class ShopScreen(private var shop: OpenShopPayload) :
    Screen(Component.translatable(if (shop.buying) "gui.lnbeconomy.shop.title.buying" else "gui.lnbeconomy.shop.title")) {
    private companion object {
        const val PANEL_WIDTH = 268
        const val PANEL_HEIGHT = 214
        const val SECTION_LEFT = 14
        const val SECTION_WIDTH = PANEL_WIDTH - 2 * SECTION_LEFT
        const val PAD = 6
        const val INNER_WIDTH = SECTION_WIDTH - 2 * PAD
        const val ITEM_TOP = 38
        const val ITEM_HEIGHT = 52
        const val ORDER_TOP = 94
        const val ORDER_HEIGHT = 48
        const val BUTTONS_TOP = 148
        const val STEP_X = 40
        const val LOTS_WIDTH = 60
        const val WHITE = 0xFFFFFFFF.toInt()
        const val RED = 0xFFFF6060.toInt()
    }

    val pos: BlockPos get() = shop.pos
    val buying: Boolean get() = shop.buying

    private var account: AccountSelector? = null
    private lateinit var lots: EditBox
    private var buy: Button? = null
    private var sell: Button? = null
    private var shownRevision = -1
    private var panelX = 0
    private var panelY = 0

    private val language: String get() = minecraft?.options?.languageCode ?: "en_us"
    private val innerX: Int get() = panelX + SECTION_LEFT + PAD

    private val carried: Int
        get() = minecraft?.player?.inventory?.let { Stock.count(it, shop.item, 0 until 36) } ?: 0

    private val quote: ShopQuote
        get() = ShopQuote(shop.lotSize, shop.sellPrice, shop.buyPrice, if (shop.buying) 0 else shop.stock, shop.room, carried)

    private val chosenLots: Int get() = lots.value.toIntOrNull() ?: 0

    /** New numbers from the server after an order, or after the shop changed. */
    fun update(payload: OpenShopPayload) {
        shop = payload
        updateButtons()
    }

    override fun init() {
        shownRevision = ClientEconomy.revision
        panelX = (width - PANEL_WIDTH) / 2
        panelY = (height - PANEL_HEIGHT) / 2
        val previous = if (::lots.isInitialized) lots.value else "1"

        val spendable = ClientEconomy.snapshot.accounts.filter { it.canSpend }
        val default = ClientEconomy.snapshot.defaultAccount?.takeIf { it in spendable } ?: spendable.firstOrNull()
        val orderY = panelY + ORDER_TOP
        account = default?.let {
            addRenderableWidget(AccountSelector(innerX, orderY + 5, INNER_WIDTH, 18, spendable, it, { entry ->
                Component.translatable("gui.lnbeconomy.shop.account.value", AccountSelector.name(entry))
            }))
        }

        val lotsY = orderY + 26
        val stepX = innerX + STEP_X
        addRenderableWidget(CasinoButton.secondary(Component.literal("-")) { step(-1) }.bounds(stepX, lotsY, 18, 18).build())
        lots = EditBox(font, stepX + 22 + 5, lotsY + 5, LOTS_WIDTH - 10, 10, Component.translatable("gui.lnbeconomy.shop.lots"))
        lots.setBordered(false)
        lots.setTextColor(CasinoUi.IVORY)
        lots.setFilter { text -> text.isEmpty() || (text.length <= 5 && text.all(Char::isDigit)) }
        lots.value = previous
        lots.setResponder { updateButtons() }
        addRenderableWidget(lots)
        addRenderableWidget(CasinoButton.secondary(Component.literal("+")) { step(1) }.bounds(stepX + 22 + LOTS_WIDTH + 4, lotsY, 18, 18).build())

        val buttonsY = panelY + BUTTONS_TOP
        val sectionX = panelX + SECTION_LEFT
        val half = (SECTION_WIDTH - 4) / 2
        if (shop.buying) {
            buy = null
            sell = addRenderableWidget(CasinoButton.primary(Component.empty()) { order(buy = false) }.bounds(sectionX, buttonsY, SECTION_WIDTH, 20).build())
        } else {
            val buyWidth = if (shop.buyPrice > 0) half else SECTION_WIDTH
            buy = addRenderableWidget(CasinoButton.primary(Component.empty()) { order(buy = true) }.bounds(sectionX, buttonsY, buyWidth, 20).build())
            sell = if (shop.buyPrice > 0) {
                addRenderableWidget(CasinoButton.primary(Component.empty()) { order(buy = false) }.bounds(sectionX + half + 4, buttonsY, half, 20).build())
            } else {
                null
            }
        }
        addRenderableWidget(
            CasinoButton.secondary(CommonComponents.GUI_DONE) { onClose() }
                .bounds(width / 2 - 45, panelY + PANEL_HEIGHT - 38, 90, 18).build(),
        )
        updateButtons()
    }

    override fun tick() {
        if (ClientEconomy.revision != shownRevision) rebuildWidgets()
        updateButtons()
    }

    private fun step(delta: Int) {
        lots.value = quote.clamp(chosenLots + delta).toString()
    }

    private fun updateButtons() {
        if (!::lots.isInitialized) return
        val quote = quote
        val n = chosenLots
        buy?.let {
            it.message = Component.translatable("gui.lnbeconomy.shop.buy", Lnbit.compact(quote.buyTotal(n), language))
            it.active = account != null && n in 1..quote.maxBuyLots
        }
        sell?.let {
            it.message = Component.translatable("gui.lnbeconomy.shop.sell", Lnbit.compact(quote.sellTotal(n), language))
            it.active = account != null && n in 1..quote.maxSellLots
        }
    }

    private fun order(buy: Boolean) {
        val n = chosenLots.takeIf { it > 0 } ?: return
        val chosen = account?.value ?: return
        // Null names the default account for a purchase, the personal one for a sale: send the ID only when it differs.
        val implied = if (buy) ClientEconomy.snapshot.defaultAccountId else ClientEconomy.snapshot.accounts.firstOrNull { it.isPersonal }?.id
        val accountId = chosen.id.takeIf { it != implied }
        val price = if (buy) shop.sellPrice else shop.buyPrice
        PacketDistributor.sendToServer(ShopOrderPayload(shop.pos, buy, n, accountId, price))
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        CasinoUi.panel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT)
        val sectionX = panelX + SECTION_LEFT
        graphics.blitSprite(CasinoUi.PANEL_INSET, sectionX, panelY + ITEM_TOP, SECTION_WIDTH, ITEM_HEIGHT)
        graphics.blitSprite(CasinoUi.PANEL_INSET, sectionX, panelY + ORDER_TOP, SECTION_WIDTH, ORDER_HEIGHT)
        graphics.blitSprite(CasinoUi.TEXT_FIELD, innerX, panelY + ITEM_TOP + 6, 40, 40)
        val field = if (lots.isFocused) CasinoUi.TEXT_FIELD_HIGHLIGHTED else CasinoUi.TEXT_FIELD
        graphics.blitSprite(field, innerX + STEP_X + 22, panelY + ORDER_TOP + 26, LOTS_WIDTH, 18)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        CasinoUi.title(graphics, font, title, width / 2, panelY + 11)
        if (shop.seller.isNotEmpty()) {
            val by = Component.translatable(if (shop.buying) "gui.lnbeconomy.shop.buyer" else "gui.lnbeconomy.shop.seller", shop.seller)
            graphics.drawCenteredString(font, by, width / 2, panelY + 25, CasinoUi.DIM)
        }

        // The item, twice its size, and what it costs.
        val itemY = panelY + ITEM_TOP
        graphics.pose().pushPose()
        graphics.pose().translate((innerX + 4).toFloat(), (itemY + 10).toFloat(), 0f)
        graphics.pose().scale(2f, 2f, 1f)
        graphics.renderItem(shop.item, 0, 0)
        graphics.pose().popPose()
        val textX = innerX + 46
        val quote = quote
        graphics.drawString(font, shop.item.hoverName, textX, itemY + 6, WHITE, true)
        if (shop.buying) {
            graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.pays", Lnbit.compact(shop.buyPrice, language), shop.lotSize), textX, itemY + 17, CasinoUi.GOLD_TEXT, true)
            val takes = if (shop.lotSize > 0) shop.room / shop.lotSize else 0
            graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.takes", takes, shop.room), textX, itemY + 28, CasinoUi.DIM, true)
            graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.carried", carried), textX, itemY + 39, if (carried >= shop.lotSize) CasinoUi.DIM else RED, true)
        } else {
            graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.price", Lnbit.compact(shop.sellPrice, language), shop.lotSize), textX, itemY + 17, CasinoUi.GOLD_TEXT, true)
            graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.stock", quote.maxBuyLots, shop.stock), textX, itemY + 28, CasinoUi.DIM, true)
            val buyBack = if (quote.buysBack) {
                Component.translatable("gui.lnbeconomy.shop.buyback", Lnbit.compact(shop.buyPrice, language), quote.maxSellLots)
            } else {
                Component.translatable("gui.lnbeconomy.shop.no_buyback")
            }
            graphics.drawString(font, buyBack, textX, itemY + 39, CasinoUi.DIM, true)
        }

        // The number of lots, and how many items that makes.
        val lotsY = panelY + ORDER_TOP + 26
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.lots"), innerX, lotsY + 5, CasinoUi.GOLD_TEXT, true)
        val items = chosenLots.toLong() * shop.lotSize
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.items_total", items), innerX + STEP_X + 22 + LOTS_WIDTH + 26, lotsY + 5, CasinoUi.DIM, true)
        if (account == null) {
            graphics.drawString(font, Component.translatable("gui.lnbeconomy.counter.no_account"), innerX, panelY + ORDER_TOP + 10, RED, true)
        }
    }

    override fun isPauseScreen(): Boolean = false
}
