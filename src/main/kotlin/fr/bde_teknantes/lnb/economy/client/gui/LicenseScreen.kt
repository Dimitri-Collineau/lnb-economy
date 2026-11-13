package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.client.ClientEconomy
import fr.bde_teknantes.lnb.economy.network.LicenseBuyPayload
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

/**
 * The licence dealer's offer: what a casino licence is, its price, how many the player holds, the account that pays,
 * and a "Buy" that asks to be clicked twice: the money is destroyed, not paid to anyone.
 */
class LicenseScreen(dealer: String) : Screen(Component.literal(dealer)) {
    private companion object {
        const val PANEL_WIDTH = 268
        const val PANEL_HEIGHT = 196
        const val LEFT = 20
        const val INNER = PANEL_WIDTH - 2 * LEFT
        const val TEXT_Y = 30
        const val PRICE_Y = 86
        const val ACCOUNT_Y = 114
        const val BUTTONS_Y = 142
        const val RED = 0xFFFF6060.toInt()
    }

    private var account: AccountSelector? = null
    private var chosenAccount: String? = null
    private lateinit var buy: Button
    private var confirming = false
    private var shownRevision = -1
    private var panelX = 0
    private var panelY = 0

    private val language: String get() = minecraft?.options?.languageCode ?: "en_us"
    private val price: Long get() = ClientEconomy.snapshot.licensePrice

    override fun init() {
        shownRevision = ClientEconomy.revision
        panelX = (width - PANEL_WIDTH) / 2
        panelY = (height - PANEL_HEIGHT) / 2
        val x = panelX + LEFT

        val spendable = ClientEconomy.snapshot.accounts.filter { it.canSpend }
        val initial = spendable.firstOrNull { it.id == chosenAccount }
            ?: ClientEconomy.snapshot.defaultAccount?.takeIf { it in spendable }
            ?: spendable.firstOrNull()
        account = initial?.let {
            val selector = object : AccountSelector(x, panelY + ACCOUNT_Y, INNER, 18, spendable, it, { entry ->
                Component.translatable("gui.lnbeconomy.counter.pay_from", name(entry))
            }) {
                override fun onPress() {
                    super.onPress()
                    chosenAccount = value.id
                    confirming = false
                    updateBuy()
                }
            }
            addRenderableWidget(selector)
        }
        val half = (INNER - 4) / 2
        buy = addRenderableWidget(CasinoButton.primary(Component.empty()) { press() }.bounds(x, panelY + BUTTONS_Y, half, 20).build())
        addRenderableWidget(CasinoButton.secondary(CommonComponents.GUI_DONE) { onClose() }.bounds(x + half + 4, panelY + BUTTONS_Y, half, 20).build())
        updateBuy()
    }

    private fun updateBuy() {
        val chosen = account?.value
        buy.message = if (confirming) Component.translatable("gui.lnbeconomy.license.confirm") else Component.translatable("gui.lnbeconomy.license.buy")
        buy.active = price >= 0 && chosen != null && chosen.balance >= price
    }

    private fun press() {
        val chosen = account?.value ?: return
        if (!confirming) {
            confirming = true
            return updateBuy()
        }
        confirming = false
        PacketDistributor.sendToServer(LicenseBuyPayload(chosen.id.takeIf { it != ClientEconomy.snapshot.defaultAccountId }))
        updateBuy()
    }

    override fun tick() {
        if (ClientEconomy.revision != shownRevision) rebuildWidgets()
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        CasinoUi.panel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT)
        graphics.blitSprite(CasinoUi.PANEL_INSET, panelX + LEFT - 6, panelY + TEXT_Y - 6, INNER + 12, 50)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val x = panelX + LEFT
        CasinoUi.title(graphics, font, Component.literal(CasinoUi.fit(font, title.string, PANEL_WIDTH - 30)), width / 2, panelY + 11)
        CasinoUi.wrapped(graphics, font, Component.translatable("gui.lnbeconomy.license.pitch"), x, panelY + TEXT_Y, INNER, 4, CasinoUi.IVORY)
        val snapshot = ClientEconomy.snapshot
        val priceText = if (price < 0) Component.translatable("gui.lnbeconomy.license.not_sold")
        else Component.translatable("gui.lnbeconomy.license.price", Lnbit.compact(price, language))
        graphics.drawString(font, priceText, x, panelY + PRICE_Y, CasinoUi.GOLD_TEXT, true)
        val held = Component.translatable("gui.lnbeconomy.license.held", snapshot.licensesFree, snapshot.licensesTotal)
        graphics.drawString(font, held, x, panelY + PRICE_Y + 11, CasinoUi.DIM, true)

        val chosen = account?.value
        val note = when {
            chosen == null -> Component.translatable("gui.lnbeconomy.counter.no_account") to RED
            price >= 0 && chosen.balance < price -> Component.translatable("gui.lnbeconomy.license.too_poor") to RED
            confirming -> Component.translatable("gui.lnbeconomy.license.warning") to CasinoUi.GOLD_TEXT
            else -> Component.translatable("gui.lnbeconomy.license.after") to CasinoUi.DIM
        }
        CasinoUi.wrapped(graphics, font, note.first, x, panelY + BUTTONS_Y + 24, INNER, 2, note.second)
    }

    override fun isPauseScreen(): Boolean = false
}
