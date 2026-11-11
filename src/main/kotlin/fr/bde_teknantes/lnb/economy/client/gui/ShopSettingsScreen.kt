package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.client.ClientEconomy
import fr.bde_teknantes.lnb.economy.money.Amounts
import fr.bde_teknantes.lnb.economy.network.AccountEntry
import fr.bde_teknantes.lnb.economy.network.ShopSavePayload
import fr.bde_teknantes.lnb.economy.shop.ShopBlockEntity
import fr.bde_teknantes.lnb.economy.shop.ShopMenu
import fr.bde_teknantes.lnb.economy.shop.ShopSettings
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.neoforged.neoforge.network.PacketDistributor

/**
 * A shop as its owners see it: the stock, the item it sells (click the template slot with an item to pick it), and its
 * prices and account. Saving sends everything to the site. Its look is temporary.
 */
class ShopSettingsScreen(menu: ShopMenu, inventory: Inventory, title: Component) :
    AbstractContainerScreen<ShopMenu>(menu, inventory, title) {
    private companion object {
        val CHEST: ResourceLocation = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png")
        const val CHEST_WIDTH = 176
        const val PANEL_WIDTH = 132
        const val FIELD_X = CHEST_WIDTH + 6
        const val FIELD_WIDTH = PANEL_WIDTH - 12
        const val DEFAULT_CAP = ShopBlockEntity.SLOTS * 64
    }

    private lateinit var lotSize: EditBox
    private lateinit var sellPrice: EditBox
    private lateinit var buyPrice: EditBox
    private lateinit var buyCap: EditBox
    private var account: CycleButton<AccountEntry>? = null
    private lateinit var save: Button

    private val language: String get() = minecraft?.options?.languageCode ?: "en_us"

    init {
        imageWidth = CHEST_WIDTH + PANEL_WIDTH
        imageHeight = 166
        inventoryLabelY = imageHeight - 94
    }

    override fun init() {
        super.init()
        val settings = menu.setup.settings
        lotSize = field(44, settings?.lotSize?.toString() ?: "1", 2)
        sellPrice = field(70, settings?.sellPrice?.toString().orEmpty(), 13)
        buyPrice = field(96, settings?.buyPrice?.toString().orEmpty(), 13)
        buyCap = field(122, (settings?.buyCap ?: DEFAULT_CAP).toString(), 5)

        val spendable = ClientEconomy.snapshot.accounts.filter { it.canSpend }
        val initial = spendable.firstOrNull { it.id == menu.setup.accountId }
            ?: ClientEconomy.snapshot.defaultAccount?.takeIf { it in spendable }
            ?: spendable.firstOrNull()
        account = initial?.let {
            addRenderableWidget(
                CycleButton.builder<AccountEntry> { entry -> accountLabel(entry) }
                    .withValues(spendable)
                    .withInitialValue(it)
                    .displayOnlyValue()
                    .create(leftPos + FIELD_X, topPos + 146, FIELD_WIDTH, 16, Component.translatable("gui.lnbeconomy.shop.sells_for")),
            )
        }
        save = addRenderableWidget(
            Button.builder(Component.translatable("gui.lnbeconomy.shop.save")) { save() }
                .bounds(leftPos + FIELD_X, topPos + 166, FIELD_WIDTH, 18).build(),
        )
    }

    private fun field(y: Int, value: String, maxLength: Int): EditBox {
        val box = EditBox(font, leftPos + FIELD_X, topPos + y, FIELD_WIDTH, 14, Component.empty())
        box.setMaxLength(maxLength)
        box.setFilter { text -> text.all(Char::isDigit) }
        box.value = value
        return addRenderableWidget(box)
    }

    override fun containerTick() {
        super.containerTick()
        save.active = account != null && !menu.templateItem.isEmpty && settings()?.problem(menu.templateItem) == null
    }

    /** The settings as typed, or null while a required field is empty. */
    private fun settings(): ShopSettings? {
        val lot = lotSize.value.toIntOrNull() ?: return null
        val sell = sellPrice.value.toLongOrNull() ?: return null
        val buy = buyPrice.value.toLongOrNull()?.takeIf { it > 0 }
        val cap = buyCap.value.toIntOrNull() ?: DEFAULT_CAP
        return ShopSettings(lot, sell, buy, cap)
    }

    private fun save() {
        val settings = settings() ?: return
        val chosen = account?.value ?: return
        PacketDistributor.sendToServer(
            ShopSavePayload(menu.setup.pos, settings.lotSize, settings.sellPrice, settings.buyPrice ?: 0, settings.buyCap, chosen.id),
        )
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // Typing in a field must not close the screen on the inventory key, nor drop items.
        val focused = focused
        if (focused is EditBox && focused.isFocused && keyCode != 256) return focused.keyPressed(keyCode, scanCode, modifiers) || true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val rows = 3
        graphics.blit(CHEST, leftPos, topPos, 0, 0, CHEST_WIDTH, rows * 18 + 17)
        graphics.blit(CHEST, leftPos, topPos + rows * 18 + 17, 0, 126, CHEST_WIDTH, 96)
        graphics.fill(leftPos + CHEST_WIDTH, topPos, leftPos + imageWidth, topPos + 190, 0xE0202020.toInt())
        // The template slot's frame.
        graphics.fill(leftPos + ShopMenu.TEMPLATE_X - 1, topPos + ShopMenu.TEMPLATE_Y - 1, leftPos + ShopMenu.TEMPLATE_X + 17, topPos + ShopMenu.TEMPLATE_Y + 17, 0xFF8B8B8B.toInt())
        graphics.fill(leftPos + ShopMenu.TEMPLATE_X, topPos + ShopMenu.TEMPLATE_Y, leftPos + ShopMenu.TEMPLATE_X + 16, topPos + ShopMenu.TEMPLATE_Y + 16, 0xFF373737.toInt())
    }

    override fun renderLabels(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        super.renderLabels(graphics, mouseX, mouseY)
        val x = FIELD_X
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.item"), x, 6, 0xFFFFFF, false)
        graphics.drawString(font, status(), ShopMenu.TEMPLATE_X + 22, ShopMenu.TEMPLATE_Y + 4, 0xA0A0A0, false)
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.lot_size"), x, 35, 0xFFFFFF, false)
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.sell_price"), x, 61, 0xFFFFFF, false)
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.buy_price"), x, 87, 0xFFFFFF, false)
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.buy_cap"), x, 113, 0xFFFFFF, false)
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.sells_for"), x, 137, 0xFFFFFF, false)
    }

    private fun status(): Component = when {
        menu.setup.offOnSite -> Component.translatable("gui.lnbeconomy.shop.status.off").withStyle(ChatFormatting.RED)
        !menu.setup.registered -> Component.translatable("gui.lnbeconomy.shop.status.unsaved").withStyle(ChatFormatting.GOLD)
        else -> Component.translatable("gui.lnbeconomy.shop.status.open").withStyle(ChatFormatting.GREEN)
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
