package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.client.ClientEconomy
import fr.bde_teknantes.lnb.economy.client.ClientTrade
import fr.bde_teknantes.lnb.economy.money.Amounts
import fr.bde_teknantes.lnb.economy.network.TradeMoneyPayload
import fr.bde_teknantes.lnb.economy.network.TradeReadyPayload
import fr.bde_teknantes.lnb.economy.trade.TradeMenu
import fr.bde_teknantes.lnb.economy.trade.TradeTerms
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.neoforged.neoforge.network.PacketDistributor

/**
 * A trade as one player sees it: their offer and the other's side by side, each with its LNBits and a ready light; the
 * account and the Ready button; a banner with the countdown, big, or the warning when the player no longer holds their
 * offer; then their inventory. What is shown comes from the menu's slots and [ClientTrade.state]; this screen only
 * draws it and sends changes. Slot positions are in [TradeMenu].
 */
class TradeScreen(menu: TradeMenu, inventory: Inventory, title: Component) :
    AbstractContainerScreen<TradeMenu>(menu, inventory, title) {
    private companion object {
        const val WIDTH = 196
        const val HEIGHT = 240
        const val TITLE_Y = 12
        const val OFFER_Y = 22
        const val OFFER_WIDTH = 86
        const val OFFER_HEIGHT = 88
        const val MINE_X = 8
        const val THEIRS_X = 102
        /** The money line, 2 px under the offer's slots. */
        const val MONEY_Y = OFFER_Y + 72
        const val FIELD_WIDTH = 60
        const val ROW_Y = OFFER_Y + OFFER_HEIGHT + 2
        const val ACCOUNT_WIDTH = 124
        const val READY_X = 136
        const val READY_WIDTH = 52
        const val BANNER_Y = ROW_Y + 19
        const val BANNER_HEIGHT = 20
        const val GREEN = 0xFF78E68C.toInt()
        const val RED = 0xFFFF6060.toInt()
        const val OUTLINE = 0xFF0C1813.toInt()
    }

    private lateinit var amount: EditBox
    private var account: AccountSelector? = null
    private lateinit var ready: Button

    private val language: String get() = minecraft?.options?.languageCode ?: "en_us"

    init {
        imageWidth = WIDTH
        imageHeight = HEIGHT
    }

    override fun init() {
        super.init()
        val state = ClientTrade.state
        amount = EditBox(font, leftPos + MINE_X + 11, topPos + MONEY_Y + 3, FIELD_WIDTH - 8, 10, Component.translatable("gui.lnbeconomy.trade.amount"))
        amount.setBordered(false)
        amount.setTextColor(CasinoUi.IVORY)
        amount.setMaxLength(13)
        amount.setFilter { text -> text.all(Char::isDigit) }
        amount.value = if (state.myAmount > 0) state.myAmount.toString() else ""
        amount.setHint(Component.literal("0").withColor(CasinoUi.HINT))
        amount.setResponder { sendMoney() }
        addRenderableWidget(amount)

        val spendable = ClientEconomy.snapshot.accounts.filter { it.canSpend }
        val initial = spendable.firstOrNull { it.id == state.myAccountId }
            ?: ClientEconomy.snapshot.defaultAccount?.takeIf { it in spendable }
            ?: spendable.firstOrNull()
        account = initial?.let {
            addRenderableWidget(object : AccountSelector(leftPos + MINE_X, topPos + ROW_Y, ACCOUNT_WIDTH, 16, spendable, it, AccountSelector::name) {
                override fun onPress() {
                    super.onPress()
                    sendMoney()
                }
            })
        }
        ready = addRenderableWidget(
            CasinoButton.primary(Component.empty()) { PacketDistributor.sendToServer(TradeReadyPayload(!ClientTrade.state.myReady)) }
                .bounds(leftPos + READY_X, topPos + ROW_Y, READY_WIDTH, 16).build(),
        )
        updateReady()
    }

    private fun sendMoney() {
        val value = amount.value.toLongOrNull() ?: 0
        val chosen = account?.value?.id
        PacketDistributor.sendToServer(TradeMoneyPayload(value, chosen.takeIf { it != ClientEconomy.snapshot.defaultAccountId }))
    }

    override fun containerTick() {
        super.containerTick()
        updateReady()
    }

    private fun updateReady() {
        ready.message = Component.translatable(if (ClientTrade.state.myReady) "gui.lnbeconomy.trade.not_ready" else "gui.lnbeconomy.trade.ready")
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // Typing an amount must not close the screen on the inventory key.
        if (amount.isFocused && keyCode != 256) return amount.keyPressed(keyCode, scanCode, modifiers) || true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val state = ClientTrade.state
        CasinoUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight)
        for ((x, isReady) in listOf(MINE_X to state.myReady, THEIRS_X to state.theirReady)) {
            graphics.blitSprite(CasinoUi.PANEL_INSET, leftPos + x, topPos + OFFER_Y, OFFER_WIDTH, OFFER_HEIGHT)
            graphics.blitSprite(if (isReady) CasinoUi.LIGHT_ON else CasinoUi.LIGHT_OFF, leftPos + x + OFFER_WIDTH - 13, topPos + OFFER_Y + 5, 7, 7)
        }
        for (slot in menu.slots) graphics.blitSprite(CasinoUi.SLOT, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18)
        val field = if (amount.isFocused) CasinoUi.TEXT_FIELD_HIGHLIGHTED else CasinoUi.TEXT_FIELD
        graphics.blitSprite(field, leftPos + MINE_X + 7, topPos + MONEY_Y, FIELD_WIDTH, 14)
        if (banner() != null) graphics.blitSprite(CasinoUi.PANEL_INSET, leftPos + 8, topPos + BANNER_Y, imageWidth - 16, BANNER_HEIGHT)
    }

    override fun renderLabels(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val state = ClientTrade.state
        CasinoUi.title(graphics, font, title, imageWidth / 2, TITLE_Y)
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.trade.you"), MINE_X + 6, OFFER_Y + 5, CasinoUi.GOLD_TEXT, true)
        graphics.drawString(font, CasinoUi.fit(font, menu.partner, OFFER_WIDTH - 12), THEIRS_X + 6, OFFER_Y + 5, CasinoUi.GOLD_TEXT, true)

        // My coin, after the field; their money, in gold. The coin sits one pixel above the digits.
        graphics.drawString(font, Lnbit.symbol(), MINE_X + 70, MONEY_Y + 2, CasinoUi.GOLD_TEXT, true)
        val plusX = THEIRS_X + 9
        graphics.drawString(font, "+", plusX, MONEY_Y + 3, CasinoUi.GOLD_TEXT, true)
        val coinX = plusX + font.width("+ ")
        graphics.drawString(font, Lnbit.symbol(), coinX, MONEY_Y + 2, CasinoUi.GOLD_TEXT, true)
        val amountX = coinX + font.width(Lnbit.symbol()) + font.width(" ")
        graphics.drawString(font, CasinoUi.fit(font, Amounts.format(state.theirAmount, language), THEIRS_X + OFFER_WIDTH - 4 - amountX), amountX, MONEY_Y + 3, CasinoUi.GOLD_TEXT, true)

        when (banner()) {
            Banner.COUNTDOWN -> countdown(graphics, state.countdown)
            Banner.MISSING -> {
                val text = Component.translatable("gui.lnbeconomy.trade.missing")
                graphics.drawString(font, text, (imageWidth - font.width(text)) / 2, BANNER_Y + 6, RED, true)
            }
            null -> graphics.drawString(font, playerInventoryTitle, 18, BANNER_Y + 8, CasinoUi.DIM, true)
        }
    }

    /** "Trading in" and the seconds left, twice as big, side by side and centered in the banner. */
    private fun countdown(graphics: GuiGraphics, seconds: Int) {
        val label = Component.translatable("gui.lnbeconomy.trade.countdown").append(" ")
        val digits = Component.literal(seconds.toString())
        val bigWidth = font.width(digits) * 2
        val x = (imageWidth - font.width(label) - bigWidth) / 2
        graphics.drawString(font, label, x, BANNER_Y + 9, GREEN, true)
        val pose = graphics.pose()
        pose.pushPose()
        pose.translate((x + font.width(label)).toFloat(), (BANNER_Y + 2).toFloat(), 0f)
        pose.scale(2f, 2f, 1f)
        font.drawInBatch8xOutline(digits.visualOrderText, 0f, 0f, GREEN, OUTLINE, pose.last().pose(), graphics.bufferSource(), 0xF000F0)
        graphics.flush()
        pose.popPose()
    }

    private enum class Banner { COUNTDOWN, MISSING }

    private fun banner(): Banner? = when {
        ClientTrade.state.countdown >= 0 -> Banner.COUNTDOWN
        !holdsOffer() -> Banner.MISSING
        else -> null
    }

    /** Whether the player still has everything they offer: the trade fails otherwise. */
    private fun holdsOffer(): Boolean {
        val inventory = minecraft?.player?.inventory ?: return true
        val offer = (0 until TradeMenu.SLOTS).map { menu.slots[it].item }
        return TradeTerms.holds(inventory, TradeTerms.totals(offer), 0 until 36)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        renderTooltip(graphics, mouseX, mouseY)
    }
}
