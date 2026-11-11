package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.client.ClientEconomy
import fr.bde_teknantes.lnb.economy.client.ClientTrade
import fr.bde_teknantes.lnb.economy.money.Amounts
import fr.bde_teknantes.lnb.economy.network.AccountEntry
import fr.bde_teknantes.lnb.economy.network.TradeMoneyPayload
import fr.bde_teknantes.lnb.economy.network.TradeReadyPayload
import fr.bde_teknantes.lnb.economy.trade.TradeMenu
import fr.bde_teknantes.lnb.economy.trade.TradeTerms
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.neoforged.neoforge.network.PacketDistributor

/**
 * A trade as one player sees it: their offer and the other's, the LNBits each gives, and the Ready button. What is
 * shown comes from the menu's slots and [ClientTrade.state]; this screen only draws it and sends changes. Its look is
 * temporary.
 */
class TradeScreen(menu: TradeMenu, inventory: Inventory, title: Component) :
    AbstractContainerScreen<TradeMenu>(menu, inventory, title) {
    private lateinit var amount: EditBox
    private var account: CycleButton<AccountEntry>? = null
    private lateinit var ready: Button

    private val language: String get() = minecraft?.options?.languageCode ?: "en_us"

    init {
        imageWidth = 176
        imageHeight = 202
        inventoryLabelY = TradeMenu.INVENTORY_Y - 10
    }

    override fun init() {
        super.init()
        val state = ClientTrade.state
        amount = EditBox(font, leftPos + TradeMenu.MINE_X, topPos + 78, 72, 14, Component.translatable("gui.lnbeconomy.trade.amount"))
        amount.setMaxLength(13)
        amount.setFilter { text -> text.all(Char::isDigit) }
        amount.value = if (state.myAmount > 0) state.myAmount.toString() else ""
        amount.setHint(Component.literal("0").withStyle(ChatFormatting.DARK_GRAY))
        amount.setResponder { sendMoney() }
        addRenderableWidget(amount)

        val spendable = ClientEconomy.snapshot.accounts.filter { it.canSpend }
        val initial = spendable.firstOrNull { it.id == state.myAccountId }
            ?: ClientEconomy.snapshot.defaultAccount?.takeIf { it in spendable }
            ?: spendable.firstOrNull()
        account = initial?.let {
            addRenderableWidget(
                CycleButton.builder<AccountEntry> { entry -> accountLabel(entry) }
                    .withValues(spendable)
                    .withInitialValue(it)
                    .displayOnlyValue()
                    .create(leftPos + TradeMenu.MINE_X, topPos + 96, 80, 16, Component.translatable("gui.lnbeconomy.trade.account")) { _, _ -> sendMoney() },
            )
        }
        ready = addRenderableWidget(
            Button.builder(Component.empty()) { PacketDistributor.sendToServer(TradeReadyPayload(!ClientTrade.state.myReady)) }
                .bounds(leftPos + TradeMenu.THEIRS_X, topPos + 96, 72, 16).build(),
        )
    }

    private fun sendMoney() {
        val value = amount.value.toLongOrNull() ?: 0
        val chosen = account?.value?.id
        PacketDistributor.sendToServer(TradeMoneyPayload(value, chosen.takeIf { it != ClientEconomy.snapshot.defaultAccountId }))
    }

    override fun containerTick() {
        super.containerTick()
        ready.message = Component.translatable(if (ClientTrade.state.myReady) "gui.lnbeconomy.trade.not_ready" else "gui.lnbeconomy.trade.ready")
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // Typing an amount must not close the screen on the inventory key.
        if (amount.isFocused && keyCode != 256) return amount.keyPressed(keyCode, scanCode, modifiers) || true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6.toInt())
        for (slot in menu.slots) {
            val x = leftPos + slot.x
            val y = topPos + slot.y
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B.toInt())
            graphics.fill(x, y, x + 16, y + 16, 0xFF373737.toInt())
        }
    }

    override fun renderLabels(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val state = ClientTrade.state
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.trade.you"), TradeMenu.MINE_X, 6, 0x404040, false)
        graphics.drawString(font, Component.literal(menu.partner), TradeMenu.THEIRS_X, 6, 0x404040, false)
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.trade.gives", Amounts.format(state.theirAmount, language)), TradeMenu.THEIRS_X, 81, 0x404040, false)
        graphics.drawString(font, mark(state.myReady), TradeMenu.MINE_X + 74, 81, 0xFFFFFF, false)
        graphics.drawString(font, mark(state.theirReady), TradeMenu.THEIRS_X + 74, 98, 0xFFFFFF, false)
        val status = when {
            state.countdown >= 0 -> Component.translatable("gui.lnbeconomy.trade.countdown", state.countdown).withStyle(ChatFormatting.DARK_GREEN)
            !holdsOffer() -> Component.translatable("gui.lnbeconomy.trade.missing").withStyle(ChatFormatting.DARK_RED)
            else -> null
        }
        status?.let { graphics.drawString(font, it, TradeMenu.MINE_X, inventoryLabelY, 0xFFFFFF, false) }
            ?: graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false)
    }

    private fun mark(ready: Boolean): Component =
        if (ready) Component.literal("✔").withStyle(ChatFormatting.DARK_GREEN) else Component.literal("…").withStyle(ChatFormatting.GRAY)

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

    private fun accountLabel(account: AccountEntry): Component {
        val name = if (account.isPersonal) Component.translatable("gui.lnbeconomy.accounts.personal").string else "#${account.handle}"
        return Component.literal("$name  ${Amounts.compact(account.balance, language)}")
    }
}
