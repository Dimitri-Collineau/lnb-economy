package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.client.ClientEconomy
import fr.bde_teknantes.lnb.economy.network.ShopMaterialPayload
import fr.bde_teknantes.lnb.economy.network.ShopSavePayload
import fr.bde_teknantes.lnb.economy.shop.ShopBlock
import fr.bde_teknantes.lnb.economy.shop.ShopBlockEntity
import fr.bde_teknantes.lnb.economy.shop.ShopMaterial
import fr.bde_teknantes.lnb.economy.shop.ShopMenu
import fr.bde_teknantes.lnb.economy.shop.ShopSettings
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor

/**
 * A shop as its owners see it: the stock and their inventory on the left, laid out like a chest, and on the right the
 * item it sells (click the template slot with an item to pick it), its prices and the account that cashes the sales.
 * Saving sends everything to the site. The button beside "Save" turns the right column into a grid of materials for
 * the shop's pedestal; a click there rebuilds it right away, and "Back" shows the settings again.
 */
class ShopSettingsScreen(menu: ShopMenu, inventory: Inventory, title: Component) :
    AbstractContainerScreen<ShopMenu>(menu, inventory, title) {
    private companion object {
        const val CHEST_WIDTH = 182
        const val SIDE_WIDTH = 128
        const val SIDE_X = CHEST_WIDTH
        const val SIDE_Y = 10
        const val FIELD_X = SIDE_X + 6
        const val FIELD_WIDTH = SIDE_WIDTH - 22
        const val FIELD_HEIGHT = 14
        const val FIRST_LABEL_Y = 50
        const val LABEL_STEP = 25
        const val ACCOUNT_Y = 151
        const val SAVE_Y = 173
        const val DEFAULT_CAP = ShopBlockEntity.SLOTS * 64
        /** The material button sits right of "Save", in the settings column. */
        const val MATERIAL_BUTTON_SIZE = 18
        const val MATERIAL_TOP = 50
        const val MATERIAL_COLUMNS = 5
        const val MATERIAL_STEP = 21
        const val GREEN = 0xFF78E68C.toInt()
        const val RED = 0xFFFF6060.toInt()
    }

    /** The status light, its text and the text's color. */
    private data class Status(val light: ResourceLocation, val text: Component, val color: Int)

    private lateinit var lotSize: EditBox
    private lateinit var sellPrice: EditBox
    private lateinit var buyPrice: EditBox
    private lateinit var buyCap: EditBox
    private val fields = mutableListOf<EditBox>()
    private var account: AccountSelector? = null
    private lateinit var save: Button

    /** Showing the pedestal materials instead of the settings. */
    private var choosingMaterial = false
    private val settingsWidgets = mutableListOf<AbstractWidget>()
    private val materialWidgets = mutableListOf<AbstractWidget>()

    /** The pedestal's material, read from the shop block the client sees. */
    private var material: ShopMaterial = ShopMaterial.DEFAULT

    init {
        imageWidth = CHEST_WIDTH + SIDE_WIDTH
        imageHeight = 206
    }

    override fun init() {
        super.init()
        fields.clear()
        settingsWidgets.clear()
        materialWidgets.clear()
        minecraft?.level?.getBlockState(menu.setup.pos)?.takeIf { it.block is ShopBlock }?.let { material = it.getValue(ShopBlock.MATERIAL) }
        val settings = menu.setup.settings
        lotSize = field(0, settings?.lotSize?.toString() ?: "1", 2)
        sellPrice = field(1, settings?.sellPrice?.toString().orEmpty(), 13)
        buyPrice = field(2, settings?.buyPrice?.toString().orEmpty(), 13)
        buyPrice.setHint(Component.translatable("gui.lnbeconomy.shop.buy_price.none").withColor(CasinoUi.HINT))
        buyCap = field(3, (settings?.buyCap ?: DEFAULT_CAP).toString(), 5)
        buyCap.setTooltip(Tooltip.create(Component.translatable("gui.lnbeconomy.shop.buy_cap.hint")))

        val spendable = ClientEconomy.snapshot.accounts.filter { it.canSpend }
        val initial = spendable.firstOrNull { it.id == menu.setup.accountId }
            ?: ClientEconomy.snapshot.defaultAccount?.takeIf { it in spendable }
            ?: spendable.firstOrNull()
        account = initial?.let {
            val selector = AccountSelector(leftPos + FIELD_X, topPos + ACCOUNT_Y, FIELD_WIDTH, 16, spendable, it, AccountSelector::name, showBalance = false)
            selector.setTooltip(Tooltip.create(Component.translatable("gui.lnbeconomy.shop.sells_for")))
            addRenderableWidget(selector).also { settingsWidgets += it }
        }
        save = addRenderableWidget(
            CasinoButton.primary(Component.translatable("gui.lnbeconomy.shop.save")) { save() }
                .bounds(leftPos + FIELD_X, topPos + SAVE_Y, FIELD_WIDTH - MATERIAL_BUTTON_SIZE - 3, 18).build(),
        )
        settingsWidgets += save
        settingsWidgets += fields

        settingsWidgets += addRenderableWidget(
            MaterialToggle(leftPos + FIELD_X + FIELD_WIDTH - MATERIAL_BUTTON_SIZE, topPos + SAVE_Y),
        )
        ShopMaterial.entries.forEachIndexed { i, choice ->
            val x = leftPos + FIELD_X + (i % MATERIAL_COLUMNS) * MATERIAL_STEP
            val y = topPos + MATERIAL_TOP + (i / MATERIAL_COLUMNS) * MATERIAL_STEP
            materialWidgets += addRenderableWidget(MaterialChoice(choice, x, y))
        }
        materialWidgets += addRenderableWidget(
            CasinoButton.secondary(CommonComponents.GUI_BACK) { showMaterials(false) }
                .bounds(leftPos + FIELD_X, topPos + SAVE_Y, FIELD_WIDTH, 18).build(),
        )
        showMaterials(choosingMaterial)
    }

    private fun showMaterials(show: Boolean) {
        choosingMaterial = show
        settingsWidgets.forEach { it.visible = !show }
        materialWidgets.forEach { it.visible = show }
        if (show) fields.forEach { it.isFocused = false }
    }

    /** The [index]th setting: a borderless box on the sunken field sprite drawn by [renderBg]. */
    private fun field(index: Int, value: String, maxLength: Int): EditBox {
        val y = FIRST_LABEL_Y + index * LABEL_STEP + 10
        val box = EditBox(font, leftPos + FIELD_X + 4, topPos + y + 3, FIELD_WIDTH - 8, 10, Component.empty())
        box.setBordered(false)
        box.setTextColor(CasinoUi.IVORY)
        box.setMaxLength(maxLength)
        box.setFilter { text -> text.all(Char::isDigit) }
        box.value = value
        fields += box
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
        CasinoUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight)
        for (slot in menu.slots) {
            if (slot.index == ShopMenu.TEMPLATE_SLOT) continue
            graphics.blitSprite(CasinoUi.SLOT, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18)
        }
        graphics.blitSprite(CasinoUi.PANEL_INSET, leftPos + SIDE_X, topPos + SIDE_Y, SIDE_WIDTH - 12, imageHeight - 22)
        // The template slot is framed in gold: it holds a copy, not a real item.
        graphics.blitSprite(CasinoUi.SECONDARY.highlighted, leftPos + ShopMenu.TEMPLATE_X - 2, topPos + ShopMenu.TEMPLATE_Y - 2, 20, 20)
        if (choosingMaterial) return
        for (box in fields) {
            val sprite = if (box.isFocused) CasinoUi.TEXT_FIELD_HIGHLIGHTED else CasinoUi.TEXT_FIELD
            graphics.blitSprite(sprite, leftPos + FIELD_X, box.y - 3, FIELD_WIDTH, FIELD_HEIGHT)
        }
        graphics.blitSprite(status().light, leftPos + ShopMenu.TEMPLATE_X + 24, topPos + ShopMenu.TEMPLATE_Y + 5, 7, 7)
    }

    override fun renderLabels(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val stockTitle = Component.translatable("gui.lnbeconomy.shop.stock_title")
        CasinoUi.title(graphics, font, stockTitle, 12 + font.width(stockTitle) / 2, 9)
        graphics.drawString(font, playerInventoryTitle, 12, 89, CasinoUi.DIM, true)
        if (choosingMaterial) {
            graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.material"), FIELD_X, 13, CasinoUi.GOLD_TEXT, true)
            return
        }
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.item"), FIELD_X, 13, CasinoUi.GOLD_TEXT, true)
        val status = status()
        graphics.drawString(font, status.text, ShopMenu.TEMPLATE_X + 34, ShopMenu.TEMPLATE_Y + 4, status.color, true)
        listOf("lot_size", "sell_price", "buy_price", "buy_cap").forEachIndexed { i, key ->
            graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.$key"), FIELD_X, FIRST_LABEL_Y + i * LABEL_STEP, CasinoUi.DIM, true)
        }
    }

    private fun status(): Status = when {
        menu.setup.offOnSite -> Status(CasinoUi.LIGHT_OFF, Component.translatable("gui.lnbeconomy.shop.status.off"), RED)
        !menu.setup.registered -> Status(CasinoUi.LIGHT_OFF, Component.translatable("gui.lnbeconomy.shop.status.unsaved"), CasinoUi.GOLD_TEXT)
        else -> Status(CasinoUi.LIGHT_ON, Component.translatable("gui.lnbeconomy.shop.status.open"), GREEN)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        renderTooltip(graphics, mouseX, mouseY)
    }

    /** Beside "Save": the pedestal's material; a click shows the materials in the right column. */
    private inner class MaterialToggle(x: Int, y: Int) :
        AbstractButton(x, y, MATERIAL_BUTTON_SIZE, MATERIAL_BUTTON_SIZE, Component.translatable("gui.lnbeconomy.shop.material")) {
        init {
            setTooltip(Tooltip.create(message))
        }

        override fun onPress() = showMaterials(true)

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            graphics.blitSprite(CasinoUi.SECONDARY.get(true, isHoveredOrFocused), x, y, width, height)
            graphics.renderFakeItem(ItemStack(material.item()), x + 1, y + 1)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }

    /** One pedestal material, shown by its block; a click rebuilds the pedestal in it right away. */
    private inner class MaterialChoice(private val choice: ShopMaterial, x: Int, y: Int) : AbstractButton(x, y, 20, 20, Component.empty()) {
        private val stack = ItemStack(choice.item())

        init {
            message = stack.hoverName
            setTooltip(Tooltip.create(stack.hoverName))
        }

        override fun onPress() {
            material = choice
            PacketDistributor.sendToServer(ShopMaterialPayload(menu.setup.pos, choice.serializedName))
        }

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            graphics.blitSprite(CasinoUi.SECONDARY.get(true, material == choice || isHoveredOrFocused), x, y, width, height)
            graphics.renderFakeItem(stack, x + 2, y + 2)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }
}
