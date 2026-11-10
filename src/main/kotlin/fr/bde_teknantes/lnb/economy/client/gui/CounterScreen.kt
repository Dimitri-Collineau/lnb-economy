package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.chip.Chips
import fr.bde_teknantes.lnb.economy.chip.Denominations
import fr.bde_teknantes.lnb.economy.client.ClientEconomy
import fr.bde_teknantes.lnb.economy.money.Amounts
import fr.bde_teknantes.lnb.economy.network.AccountEntry
import fr.bde_teknantes.lnb.economy.network.CounterBuyPayload
import fr.bde_teknantes.lnb.economy.network.CounterCashPayload
import fr.bde_teknantes.lnb.economy.network.CounterChangePayload
import fr.bde_teknantes.lnb.economy.network.OpenCounterPayload
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

/** A casino's counter: buy chips from an account, cash all of this casino's chips, or get change. */
class CounterScreen(private val counter: OpenCounterPayload) :
    Screen(Component.translatable("gui.lnbeconomy.counter.title", counter.casinoName)) {
    private companion object {
        const val WIDTH = 240
    }

    private var account: CycleButton<AccountEntry>? = null
    private lateinit var amount: EditBox
    private lateinit var buy: Button
    private lateinit var cash: Button
    private var largest = Denominations.ALL.last()
    private var shownRevision = -1

    private val language: String get() = minecraft?.options?.languageCode ?: "en_us"
    private val left: Int get() = (width - WIDTH) / 2

    override fun init() {
        shownRevision = ClientEconomy.revision
        val top = height / 2 - 80
        val previous = if (::amount.isInitialized) amount.value else ""

        // Buying. Without an account to pay from (no economy, or not loaded yet), a disabled placeholder.
        val spendable = ClientEconomy.snapshot.accounts.filter { it.canSpend && it.handle != counter.handle }
        val default = ClientEconomy.snapshot.defaultAccount?.takeIf { it in spendable } ?: spendable.firstOrNull()
        account = if (default == null) {
            null.also {
                addRenderableWidget(Button.builder(Component.translatable("gui.lnbeconomy.counter.no_account")) {}
                    .bounds(left, top + 20, WIDTH, 20).build()).active = false
            }
        } else {
            addRenderableWidget(
                CycleButton.builder<AccountEntry> { accountLabel(it) }
                    .withValues(spendable)
                    .withInitialValue(default)
                    .create(left, top + 20, WIDTH, 20, Component.translatable("gui.lnbeconomy.counter.pay_from")),
            )
        }

        amount = EditBox(font, left, top + 44, WIDTH - 84, 20, Component.translatable("gui.lnbeconomy.counter.amount"))
        amount.setFilter { text -> text.isEmpty() || (text.length <= 13 && text.all(Char::isDigit)) }
        amount.setHint(Component.translatable("gui.lnbeconomy.counter.amount").withStyle(ChatFormatting.DARK_GRAY))
        amount.value = previous
        amount.setResponder { updateButtons() }
        addRenderableWidget(amount)
        buy = addRenderableWidget(Button.builder(Component.translatable("gui.lnbeconomy.counter.buy")) { buy() }
            .bounds(left + WIDTH - 80, top + 44, 80, 20).build())

        // Cashing
        cash = addRenderableWidget(Button.builder(Component.translatable("gui.lnbeconomy.counter.cash")) {
            PacketDistributor.sendToServer(CounterCashPayload(counter.pos))
        }.bounds(left, top + 92, WIDTH, 20).build())

        // Change
        addRenderableWidget(
            CycleButton.builder<Int> { Component.literal(Amounts.format(it.toLong(), language)) }
                .withValues(Denominations.ALL.reversed())
                .withInitialValue(largest)
                .create(left, top + 136, WIDTH - 84, 20, Component.translatable("gui.lnbeconomy.counter.largest")) { _, value -> largest = value },
        )
        addRenderableWidget(Button.builder(Component.translatable("gui.lnbeconomy.counter.change")) {
            PacketDistributor.sendToServer(CounterChangePayload(counter.pos, largest))
        }.bounds(left + WIDTH - 80, top + 136, 80, 20).build())

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE) { onClose() }.bounds(width / 2 - 50, top + 168, 100, 20).build())
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
        amount.value = ""
    }

    private fun chipsValue(): Long = minecraft?.player?.inventory?.let { Chips.valueIn(it, counter.handle) } ?: 0

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val top = height / 2 - 80
        graphics.drawCenteredString(font, title, width / 2, top - 14, 0xFFFFFF)
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.counter.buy.title"), left, top + 8, 0xFFD45A)
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.counter.cash.title", Amounts.compact(chipsValue(), language)), left, top + 80, 0xFFD45A)
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.counter.change.title"), left, top + 124, 0xFFD45A)
    }

    override fun isPauseScreen(): Boolean = false

    private fun accountLabel(account: AccountEntry): Component {
        val name = if (account.isPersonal) Component.translatable("gui.lnbeconomy.accounts.personal").string else "#${account.handle}"
        return Component.literal("$name  ${Amounts.compact(account.balance, language)}")
    }
}
