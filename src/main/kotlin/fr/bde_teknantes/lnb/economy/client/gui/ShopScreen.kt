package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.client.ClientEconomy
import fr.bde_teknantes.lnb.economy.money.Amounts
import fr.bde_teknantes.lnb.economy.network.AccountEntry
import fr.bde_teknantes.lnb.economy.network.OpenShopPayload
import fr.bde_teknantes.lnb.economy.network.ShopOrderPayload
import fr.bde_teknantes.lnb.economy.shop.ShopQuote
import fr.bde_teknantes.lnb.economy.shop.Stock
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

/**
 * A shop as a customer sees it: the item, its price per lot, and buying lots from it or selling them back. The
 * numbers come from [ShopQuote]; this screen only draws them and sends the order. Its look is temporary.
 */
class ShopScreen(private var shop: OpenShopPayload) : Screen(Component.translatable("gui.lnbeconomy.shop.title")) {
    private companion object {
        const val WIDTH = 240
    }

    val pos: BlockPos get() = shop.pos

    private var account: CycleButton<AccountEntry>? = null
    private lateinit var lots: EditBox
    private lateinit var buy: Button
    private var sell: Button? = null
    private var shownRevision = -1

    private val language: String get() = minecraft?.options?.languageCode ?: "en_us"
    private val left: Int get() = (width - WIDTH) / 2
    private val top: Int get() = height / 2 - 80

    private val carried: Int
        get() = minecraft?.player?.inventory?.let { Stock.count(it, shop.item, 0 until 36) } ?: 0

    private val quote: ShopQuote
        get() = ShopQuote(shop.lotSize, shop.sellPrice, shop.buyPrice, shop.stock, shop.room, carried)

    private val chosenLots: Int get() = lots.value.toIntOrNull() ?: 0

    /** New numbers from the server after an order, or after the shop changed. */
    fun update(payload: OpenShopPayload) {
        shop = payload
        updateButtons()
    }

    override fun init() {
        shownRevision = ClientEconomy.revision
        val previous = if (::lots.isInitialized) lots.value else "1"

        val spendable = ClientEconomy.snapshot.accounts.filter { it.canSpend }
        val default = ClientEconomy.snapshot.defaultAccount?.takeIf { it in spendable } ?: spendable.firstOrNull()
        account = default?.let {
            addRenderableWidget(
                CycleButton.builder<AccountEntry> { entry -> accountLabel(entry) }
                    .withValues(spendable)
                    .withInitialValue(it)
                    .create(left, top + 56, WIDTH, 20, Component.translatable("gui.lnbeconomy.shop.account")),
            )
        }

        addRenderableWidget(Button.builder(Component.literal("-")) { step(-1) }.bounds(left, top + 80, 20, 20).build())
        lots = EditBox(font, left + 24, top + 80, WIDTH - 48, 20, Component.translatable("gui.lnbeconomy.shop.lots"))
        lots.setFilter { text -> text.isEmpty() || (text.length <= 5 && text.all(Char::isDigit)) }
        lots.value = previous
        lots.setResponder { updateButtons() }
        addRenderableWidget(lots)
        addRenderableWidget(Button.builder(Component.literal("+")) { step(1) }.bounds(left + WIDTH - 20, top + 80, 20, 20).build())

        buy = addRenderableWidget(Button.builder(Component.empty()) { order(buy = true) }.bounds(left, top + 108, WIDTH, 20).build())
        sell = if (shop.buyPrice > 0) {
            addRenderableWidget(Button.builder(Component.empty()) { order(buy = false) }.bounds(left, top + 132, WIDTH, 20).build())
        } else {
            null
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE) { onClose() }.bounds(width / 2 - 50, top + 160, 100, 20).build())
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
        buy.message = Component.translatable("gui.lnbeconomy.shop.buy", Amounts.format(quote.buyTotal(n), language))
        buy.active = account != null && n in 1..quote.maxBuyLots
        sell?.let {
            it.message = Component.translatable("gui.lnbeconomy.shop.sell", Amounts.format(quote.sellTotal(n), language))
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

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        graphics.drawCenteredString(font, title, width / 2, top - 14, 0xFFFFFF)
        graphics.renderItem(shop.item, left, top)
        graphics.drawString(font, shop.item.hoverName, left + 22, top + 4, 0xFFFFFF)
        if (shop.seller.isNotEmpty()) {
            graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.seller", shop.seller), left + WIDTH - font.width(Component.translatable("gui.lnbeconomy.shop.seller", shop.seller)), top + 4, 0xA0A0A0)
        }
        val quote = quote
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.price", Amounts.format(shop.sellPrice, language), shop.lotSize), left, top + 22, 0xFFD45A)
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.stock", quote.maxBuyLots, shop.stock), left, top + 34, 0xA0A0A0)
        val buyBack = if (quote.buysBack) {
            Component.translatable("gui.lnbeconomy.shop.buyback", Amounts.format(shop.buyPrice, language), quote.maxSellLots)
        } else {
            Component.translatable("gui.lnbeconomy.shop.no_buyback")
        }
        graphics.drawString(font, buyBack, left, top + 44, 0xA0A0A0)
        if (account == null) {
            graphics.drawString(font, Component.translatable("gui.lnbeconomy.counter.no_account").withStyle(ChatFormatting.RED), left, top + 62, 0xFFFFFF)
        }
    }

    override fun isPauseScreen(): Boolean = false

    private fun accountLabel(account: AccountEntry): Component {
        val name = if (account.isPersonal) Component.translatable("gui.lnbeconomy.accounts.personal").string else "#${account.handle}"
        return Component.literal("$name  ").append(Lnbit.compact(account.balance, language))
    }
}
