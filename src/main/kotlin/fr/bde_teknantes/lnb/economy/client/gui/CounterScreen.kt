package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.ModContent
import fr.bde_teknantes.lnb.economy.chip.Chips
import fr.bde_teknantes.lnb.economy.chip.Denominations
import fr.bde_teknantes.lnb.economy.client.ClientEconomy
import fr.bde_teknantes.lnb.economy.network.AccountEntry
import fr.bde_teknantes.lnb.economy.network.CounterBuyPayload
import fr.bde_teknantes.lnb.economy.network.CounterCashPayload
import fr.bde_teknantes.lnb.economy.network.CounterChangePayload
import fr.bde_teknantes.lnb.economy.network.OpenCounterPayload
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor

/** A casino's counter: buy chips from an account, cash all of this casino's chips, or get change. */
class CounterScreen(private val counter: OpenCounterPayload) :
    Screen(Component.translatable("gui.lnbeconomy.counter.title", counter.casinoName)) {
    private companion object {
        const val PANEL_WIDTH = 268
        const val PANEL_HEIGHT = 228
        const val SECTION_LEFT = 14
        const val SECTION_WIDTH = PANEL_WIDTH - 2 * SECTION_LEFT
        const val PAD = 6
        const val INNER_WIDTH = SECTION_WIDTH - 2 * PAD
        const val BUY_TOP = 26
        const val BUY_HEIGHT = 62
        const val CASH_TOP = 92
        const val CASH_HEIGHT = 40
        const val CHANGE_TOP = 136
        const val CHANGE_HEIGHT = 46
        const val ACTION_WIDTH = 70
        const val CHIP_SIZE = 20
        const val CHIP_STEP = 22
    }

    private var account: AccountCycle? = null
    private lateinit var amount: EditBox
    private lateinit var buy: Button
    private lateinit var cash: Button
    private var largest = Denominations.ALL.last()
    private var shownRevision = -1
    private var panelX = 0
    private var panelY = 0

    private val language: String get() = minecraft?.options?.languageCode ?: "en_us"
    private val sectionX: Int get() = panelX + SECTION_LEFT
    private val innerX: Int get() = sectionX + PAD

    override fun init() {
        shownRevision = ClientEconomy.revision
        panelX = (width - PANEL_WIDTH) / 2
        panelY = (height - PANEL_HEIGHT) / 2
        val previous = if (::amount.isInitialized) amount.value else ""

        // Buying. Without an account to pay from (no economy, or not loaded yet), a disabled placeholder.
        val spendable = ClientEconomy.snapshot.accounts.filter { it.canSpend && it.handle != counter.handle }
        val default = ClientEconomy.snapshot.defaultAccount?.takeIf { it in spendable } ?: spendable.firstOrNull()
        val accountY = panelY + BUY_TOP + 16
        account = if (default == null) {
            null.also {
                addRenderableWidget(
                    CasinoButton.secondary(Component.translatable("gui.lnbeconomy.counter.no_account")) {}
                        .bounds(innerX, accountY, INNER_WIDTH, 18).build(),
                ).active = false
            }
        } else {
            addRenderableWidget(AccountCycle(spendable, default, innerX, accountY))
        }

        val fieldY = accountY + 22
        amount = EditBox(font, innerX + 5, fieldY + 5, INNER_WIDTH - ACTION_WIDTH - 4 - 10, 10, Component.translatable("gui.lnbeconomy.counter.amount"))
        amount.setBordered(false)
        amount.setTextColor(CasinoUi.IVORY)
        amount.setFilter { text -> text.isEmpty() || (text.length <= 13 && text.all(Char::isDigit)) }
        amount.setHint(Component.translatable("gui.lnbeconomy.counter.amount").withColor(CasinoUi.HINT))
        amount.value = previous
        amount.setResponder { updateButtons() }
        addRenderableWidget(amount)
        buy = addRenderableWidget(
            CasinoButton.primary(Component.translatable("gui.lnbeconomy.counter.buy")) { buy() }
                .bounds(innerX + INNER_WIDTH - ACTION_WIDTH, fieldY, ACTION_WIDTH, 18).build(),
        )

        // Cashing
        cash = addRenderableWidget(
            CasinoButton.primary(Component.translatable("gui.lnbeconomy.counter.cash")) {
                PacketDistributor.sendToServer(CounterCashPayload(counter.pos))
            }.bounds(innerX, panelY + CASH_TOP + 16, INNER_WIDTH, 18).build(),
        )

        // Change: pick the largest chip to receive by its icon
        val chipsY = panelY + CHANGE_TOP + 18
        Denominations.ALL.forEachIndexed { i, value ->
            addRenderableWidget(ChipChoice(value, innerX + i * CHIP_STEP, chipsY))
        }
        addRenderableWidget(
            CasinoButton.primary(Component.translatable("gui.lnbeconomy.counter.change")) {
                PacketDistributor.sendToServer(CounterChangePayload(counter.pos, largest))
            }.bounds(innerX + INNER_WIDTH - ACTION_WIDTH, chipsY, ACTION_WIDTH, CHIP_SIZE).build(),
        )

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

    private fun updateButtons() {
        val value = amount.value.toLongOrNull()
        buy.active = account != null && value != null && value > 0
        cash.active = chipsValue() > 0
    }

    private fun buy() {
        val value = amount.value.toLongOrNull() ?: return
        val chosen = account?.value ?: return
        val accountId = chosen.id.takeIf { it != ClientEconomy.snapshot.defaultAccountId }
        PacketDistributor.sendToServer(CounterBuyPayload(counter.pos, accountId, value))
        // The answer comes in the chat, and the chips in the inventory: nothing more to do here.
        onClose()
    }

    private fun chipsValue(): Long = minecraft?.player?.inventory?.let { Chips.valueIn(it, counter.handle) } ?: 0

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        CasinoUi.panel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT)
        for ((top, h) in listOf(BUY_TOP to BUY_HEIGHT, CASH_TOP to CASH_HEIGHT, CHANGE_TOP to CHANGE_HEIGHT)) {
            graphics.blitSprite(CasinoUi.PANEL_INSET, sectionX, panelY + top, SECTION_WIDTH, h)
        }
        val field = if (amount.isFocused) CasinoUi.TEXT_FIELD_HIGHLIGHTED else CasinoUi.TEXT_FIELD
        graphics.blitSprite(field, innerX, amount.y - 5, INNER_WIDTH - ACTION_WIDTH - 4, 18)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        CasinoUi.title(graphics, font, Component.literal(CasinoUi.fit(font, title.string, PANEL_WIDTH - 30)), width / 2, panelY + 11)
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.counter.buy.title"), innerX, panelY + BUY_TOP + 5, CasinoUi.GOLD_TEXT, true)
        graphics.drawString(
            font, Component.translatable("gui.lnbeconomy.counter.cash.title", Lnbit.compact(chipsValue(), language)),
            innerX, panelY + CASH_TOP + 5, CasinoUi.GOLD_TEXT, true,
        )
        val end = graphics.drawString(font, Component.translatable("gui.lnbeconomy.counter.change.title"), innerX, panelY + CHANGE_TOP + 5, CasinoUi.GOLD_TEXT, true)
        graphics.drawString(font, Component.literal(" ").append(Component.translatable("gui.lnbeconomy.counter.change.until")), end, panelY + CHANGE_TOP + 5, CasinoUi.DIM, true)
    }

    override fun isPauseScreen(): Boolean = false

    private fun accountName(account: AccountEntry): Component =
        if (account.isPersonal) Component.translatable("gui.lnbeconomy.accounts.personal") else Component.literal("#${account.handle}")

    /** The account that pays: icon, name and balance. A click moves to the next spendable account. */
    private inner class AccountCycle(private val choices: List<AccountEntry>, initial: AccountEntry, x: Int, y: Int) :
        AbstractButton(x, y, INNER_WIDTH, 18, Component.empty()) {

        var value: AccountEntry = initial
            private set

        init {
            message = label()
        }

        private fun label(): Component = Component.translatable("gui.lnbeconomy.counter.pay_from", accountName(value))

        override fun onPress() {
            value = choices[(choices.indexOf(value) + 1) % choices.size]
            message = label()
        }

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            graphics.blitSprite(CasinoUi.SECONDARY.get(active, isHoveredOrFocused), x, y, width, height)
            graphics.blitSprite(if (value.isPersonal) CasinoUi.ACCOUNT_PERSONAL else CasinoUi.ACCOUNT_SHARED, x + 5, y + 5, 9, 9)
            val balance = Lnbit.compact(value.balance, language)
            val balanceX = x + width - 14 - font.width(balance)
            graphics.drawString(font, CasinoUi.fit(font, message.string, balanceX - 4 - (x + 18)), x + 18, y + 5, CasinoUi.IVORY, true)
            graphics.drawString(font, balance, balanceX, y + 5, CasinoUi.GOLD_TEXT, true)
            if (choices.size > 1) graphics.blitSprite(CasinoUi.PAGE_FORWARD, x + width - 9, y + 4, 7, 11)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }

    /** One chip value in the change picker, shown by its item. The chosen one is lit. */
    private inner class ChipChoice(private val value: Int, x: Int, y: Int) :
        AbstractButton(x, y, CHIP_SIZE, CHIP_SIZE, Component.empty()) {

        private val stack = ItemStack(ModContent.CHIPS.getValue(value).get())

        init {
            message = stack.hoverName
            setTooltip(Tooltip.create(stack.hoverName))
        }

        override fun onPress() {
            largest = value
        }

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            graphics.blitSprite(CasinoUi.SECONDARY.get(true, largest == value || isHoveredOrFocused), x, y, width, height)
            graphics.renderFakeItem(stack, x + 2, y + 2)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }
}
