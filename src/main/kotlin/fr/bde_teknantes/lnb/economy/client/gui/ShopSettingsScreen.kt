package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.client.ClientEconomy
import fr.bde_teknantes.lnb.economy.network.ShopItemPayload
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
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.network.PacketDistributor

/**
 * A shop as its owners see it: the stock and their inventory on the left, laid out like a chest, and on the right what
 * the shop does. At the top, the owner chooses whether the shop **sells** its item (and may buy it back) or only
 * **buys** it from players. A shop that sells takes its item from a click on the item slot with it; a shop that buys
 * can also pick any item of the game from a search. Then come the prices and the account. Saving sends everything to
 * the site. The button beside "Save" shows the pedestal's materials; a click there rebuilds it right away.
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
        const val MODE_Y = 11
        const val MODE_HEIGHT = 12
        const val FIRST_LABEL_Y = 50
        const val LABEL_STEP = 25
        const val ACCOUNT_Y = 151
        const val SAVE_Y = 173
        const val DEFAULT_CAP = ShopBlockEntity.SLOTS * 64
        /** The material button sits right of "Save", in the settings column. */
        const val MATERIAL_BUTTON_SIZE = 18
        const val GRID_TOP = 50
        const val GRID_COLUMNS = 5
        const val GRID_STEP = 21
        /** The item search: its box, then the items found. */
        const val SEARCH_Y = 48
        const val ITEMS_TOP = 68
        const val ITEM_ROWS = 5
        const val GREEN = 0xFF78E68C.toInt()
        const val RED = 0xFFFF6060.toInt()

        const val LOT = 0
        const val SELL = 1
        const val BUY = 2
        const val CAP = 3
    }

    /** What the right column shows. */
    private enum class View { SETTINGS, MATERIALS, ITEMS }

    /** The status light, its text and the text's color. */
    private data class Status(val light: ResourceLocation, val text: Component, val color: Int)

    /** What was typed in each field (see [LOT] and the others), kept while the column changes. */
    private val typed = arrayOfNulls<String>(4)
    private val boxes = arrayOfNulls<EditBox>(4)
    private var buying: Boolean? = null
    private var account: AccountSelector? = null
    private var chosenAccount: String? = null
    private lateinit var save: Button
    private var view = View.SETTINGS
    private var search: EditBox? = null
    private var query = ""
    private var itemScroll = 0
    private val settingsWidgets = mutableListOf<AbstractWidget>()

    /** The pedestal's material, read from the shop block the client sees. */
    private var material: ShopMaterial = ShopMaterial.DEFAULT

    init {
        imageWidth = CHEST_WIDTH + SIDE_WIDTH
        imageHeight = 206
    }

    private val isBuying: Boolean get() = buying ?: (menu.setup.settings?.buying ?: false)

    /** The fields shown, top to bottom, with their label keys: a shop that buys has no selling price. */
    private fun rows(): List<Pair<Int, String>> =
        if (isBuying) listOf(LOT to "lot_size", BUY to "buying_price", CAP to "buying_cap")
        else listOf(LOT to "lot_size", SELL to "sell_price", BUY to "buy_price", CAP to "buy_cap")

    override fun init() {
        super.init()
        settingsWidgets.clear()
        boxes.fill(null)
        search = null
        minecraft?.level?.getBlockState(menu.setup.pos)?.takeIf { it.block is ShopBlock }?.let { material = it.getValue(ShopBlock.MATERIAL) }
        val settings = menu.setup.settings
        if (typed[LOT] == null) {
            typed[LOT] = settings?.lotSize?.toString() ?: "1"
            typed[SELL] = settings?.takeIf { !it.buying }?.sellPrice?.toString().orEmpty()
            typed[BUY] = settings?.buyPrice?.toString().orEmpty()
            typed[CAP] = (settings?.buyCap ?: DEFAULT_CAP).toString()
        }
        when (view) {
            View.SETTINGS -> initSettings()
            View.MATERIALS -> initMaterials()
            View.ITEMS -> initItems()
        }
    }

    private fun initSettings() {
        val half = (FIELD_WIDTH - 2) / 2
        addRenderableWidget(ModeChoice(false, leftPos + FIELD_X, topPos + MODE_Y, half))
        addRenderableWidget(ModeChoice(true, leftPos + FIELD_X + half + 2, topPos + MODE_Y, FIELD_WIDTH - half - 2))

        rows().forEachIndexed { row, (field, _) ->
            val maxLength = when (field) {
                LOT -> 2
                CAP -> 5
                else -> 13
            }
            boxes[field] = field(row, field, maxLength)
        }
        if (!isBuying) boxes[BUY]?.setHint(Component.translatable("gui.lnbeconomy.shop.buy_price.none").withColor(CasinoUi.HINT))
        boxes[CAP]?.setTooltip(Tooltip.create(Component.translatable("gui.lnbeconomy.shop.buy_cap.hint")))
        if (isBuying) {
            // The row a selling price would take: any item of the game, from a search.
            addRenderableWidget(
                CasinoButton.secondary(Component.translatable("gui.lnbeconomy.shop.search_item")) { show(View.ITEMS) }
                    .bounds(leftPos + FIELD_X, topPos + FIRST_LABEL_Y + 3 * LABEL_STEP + 6, FIELD_WIDTH, 16).build(),
            )
        }

        val spendable = ClientEconomy.snapshot.accounts.filter { it.canSpend }
        val initial = spendable.firstOrNull { it.id == (chosenAccount ?: menu.setup.accountId) }
            ?: ClientEconomy.snapshot.defaultAccount?.takeIf { it in spendable }
            ?: spendable.firstOrNull()
        account = initial?.let {
            val selector = object : AccountSelector(leftPos + FIELD_X, topPos + ACCOUNT_Y, FIELD_WIDTH, 16, spendable, it, AccountSelector::name, showBalance = false) {
                override fun onPress() {
                    super.onPress()
                    chosenAccount = value.id
                }
            }
            selector.setTooltip(Tooltip.create(Component.translatable(if (isBuying) "gui.lnbeconomy.shop.pays_from" else "gui.lnbeconomy.shop.sells_for")))
            addRenderableWidget(selector)
        }
        save = addRenderableWidget(
            CasinoButton.primary(Component.translatable("gui.lnbeconomy.shop.save")) { save() }
                .bounds(leftPos + FIELD_X, topPos + SAVE_Y, FIELD_WIDTH - MATERIAL_BUTTON_SIZE - 3, 18).build(),
        )
        addRenderableWidget(MaterialToggle(leftPos + FIELD_X + FIELD_WIDTH - MATERIAL_BUTTON_SIZE, topPos + SAVE_Y))
    }

    private fun initMaterials() {
        ShopMaterial.entries.forEachIndexed { i, choice ->
            val x = leftPos + FIELD_X + (i % GRID_COLUMNS) * GRID_STEP
            val y = topPos + GRID_TOP + (i / GRID_COLUMNS) * GRID_STEP
            addRenderableWidget(MaterialChoice(choice, x, y))
        }
        addBack()
    }

    private fun initItems() {
        val box = CasinoUi.editBox(font, leftPos + FIELD_X, topPos + SEARCH_Y, FIELD_WIDTH, Component.translatable("gui.lnbeconomy.shop.search_item.hint"), 32)
        box.value = query
        box.setResponder { text ->
            if (text == query) return@setResponder
            query = text
            itemScroll = 0
            rebuildWidgets()
        }
        search = addRenderableWidget(box)
        setInitialFocus(box)
        val found = foundItems()
        itemScroll = itemScroll.coerceIn(0, maxScroll(found.size))
        found.drop(itemScroll * GRID_COLUMNS).take(ITEM_ROWS * GRID_COLUMNS).forEachIndexed { i, item ->
            val x = leftPos + FIELD_X + (i % GRID_COLUMNS) * GRID_STEP
            val y = topPos + ITEMS_TOP + (i / GRID_COLUMNS) * GRID_STEP
            addRenderableWidget(ItemChoice(item, x, y))
        }
        addBack()
    }

    private fun addBack() {
        addRenderableWidget(
            CasinoButton.secondary(CommonComponents.GUI_BACK) { show(View.SETTINGS) }
                .bounds(leftPos + FIELD_X, topPos + SAVE_Y, FIELD_WIDTH, 18).build(),
        )
    }

    private fun show(next: View) {
        view = next
        rebuildWidgets()
    }

    /** Every item of the game whose name holds what was typed, in the registry's order. */
    private fun foundItems(): List<Item> {
        val wanted = query.trim().lowercase()
        return BuiltInRegistries.ITEM.filter { item ->
            item !== Items.AIR && (wanted.isEmpty() || ItemStack(item).hoverName.string.lowercase().contains(wanted) ||
                BuiltInRegistries.ITEM.getKey(item).toString().contains(wanted))
        }
    }

    private fun maxScroll(found: Int): Int = maxOf(0, (found + GRID_COLUMNS - 1) / GRID_COLUMNS - ITEM_ROWS)

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (view != View.ITEMS) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        val before = itemScroll
        itemScroll = (itemScroll - scrollY.toInt()).coerceIn(0, maxScroll(foundItems().size))
        if (itemScroll != before) rebuildWidgets()
        return true
    }

    /** The setting [field] on [row]: a borderless box on the sunken field sprite drawn by [renderBg]. */
    private fun field(row: Int, field: Int, maxLength: Int): EditBox {
        val y = FIRST_LABEL_Y + row * LABEL_STEP + 10
        val box = EditBox(font, leftPos + FIELD_X + 4, topPos + y + 3, FIELD_WIDTH - 8, 10, Component.empty())
        box.setBordered(false)
        box.setTextColor(CasinoUi.IVORY)
        box.setMaxLength(maxLength)
        box.setFilter { text -> text.all(Char::isDigit) }
        box.value = typed[field].orEmpty()
        box.setResponder { typed[field] = it }
        return addRenderableWidget(box)
    }

    override fun containerTick() {
        super.containerTick()
        if (view == View.SETTINGS) save.active = account != null && !menu.templateItem.isEmpty && settings()?.problem(menu.templateItem) == null
    }

    /** The settings as typed, or null while a required field is empty. */
    private fun settings(): ShopSettings? {
        val lot = typed[LOT]?.toIntOrNull() ?: return null
        val buy = typed[BUY]?.toLongOrNull()?.takeIf { it > 0 }
        val cap = typed[CAP]?.toIntOrNull() ?: DEFAULT_CAP
        if (isBuying) return ShopSettings.buying(lot, buy ?: return null, cap)
        val sell = typed[SELL]?.toLongOrNull() ?: return null
        return ShopSettings(lot, sell, buy, cap)
    }

    private fun save() {
        val settings = settings() ?: return
        val chosen = account?.value ?: return
        PacketDistributor.sendToServer(
            ShopSavePayload(menu.setup.pos, settings.lotSize, settings.sellPrice, settings.buyPrice ?: 0, settings.buyCap, chosen.id, settings.buying),
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
        search?.let { CasinoUi.field(graphics, it) }
        if (view != View.SETTINGS) return
        for (box in boxes.filterNotNull()) {
            val sprite = if (box.isFocused) CasinoUi.TEXT_FIELD_HIGHLIGHTED else CasinoUi.TEXT_FIELD
            graphics.blitSprite(sprite, leftPos + FIELD_X, box.y - 3, FIELD_WIDTH, FIELD_HEIGHT)
        }
        graphics.blitSprite(status().light, leftPos + ShopMenu.TEMPLATE_X + 24, topPos + ShopMenu.TEMPLATE_Y + 5, 7, 7)
    }

    override fun renderLabels(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val stockTitle = Component.translatable("gui.lnbeconomy.shop.stock_title")
        CasinoUi.title(graphics, font, stockTitle, 12 + font.width(stockTitle) / 2, 9)
        graphics.drawString(font, playerInventoryTitle, 12, 89, CasinoUi.DIM, true)
        when (view) {
            View.MATERIALS -> graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.material"), FIELD_X, 13, CasinoUi.GOLD_TEXT, true)
            View.ITEMS -> {
                graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.item_bought"), FIELD_X, 13, CasinoUi.GOLD_TEXT, true)
                if (!menu.templateItem.isEmpty) {
                    graphics.drawString(font, CasinoUi.fit(font, menu.templateItem.hoverName.string, FIELD_WIDTH - 26), ShopMenu.TEMPLATE_X + 24, ShopMenu.TEMPLATE_Y + 4, CasinoUi.IVORY, true)
                }
            }
            View.SETTINGS -> {
                val status = status()
                graphics.drawString(font, status.text, ShopMenu.TEMPLATE_X + 34, ShopMenu.TEMPLATE_Y + 4, status.color, true)
                rows().forEachIndexed { row, (_, key) ->
                    graphics.drawString(font, Component.translatable("gui.lnbeconomy.shop.$key"), FIELD_X, FIRST_LABEL_Y + row * LABEL_STEP, CasinoUi.DIM, true)
                }
            }
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

    /** "Sells" or "Buys": what the shop does. The chosen one is lit. */
    private inner class ModeChoice(private val buys: Boolean, x: Int, y: Int, width: Int) :
        AbstractButton(x, y, width, MODE_HEIGHT, Component.translatable(if (buys) "gui.lnbeconomy.shop.mode.buys" else "gui.lnbeconomy.shop.mode.sells")) {
        init {
            setTooltip(Tooltip.create(Component.translatable(if (buys) "gui.lnbeconomy.shop.mode.buys.hint" else "gui.lnbeconomy.shop.mode.sells.hint")))
        }

        override fun onPress() {
            if (isBuying == buys) return
            buying = buys
            rebuildWidgets()
        }

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val chosen = isBuying == buys
            graphics.blitSprite(if (chosen) CasinoUi.TAB_SELECTED else CasinoUi.TAB, x, y, width, height)
            graphics.drawCenteredString(font, message, x + width / 2, y + 2, if (chosen) CasinoUi.GOLD_TEXT else if (isHoveredOrFocused) CasinoUi.IVORY else CasinoUi.DIM)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }

    /** Beside "Save": the pedestal's material; a click shows the materials in the right column. */
    private inner class MaterialToggle(x: Int, y: Int) :
        AbstractButton(x, y, MATERIAL_BUTTON_SIZE, MATERIAL_BUTTON_SIZE, Component.translatable("gui.lnbeconomy.shop.material")) {
        init {
            setTooltip(Tooltip.create(message))
        }

        override fun onPress() = show(View.MATERIALS)

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

    /** One item found by the search; a click makes it the shop's item and goes back to the settings. */
    private inner class ItemChoice(item: Item, x: Int, y: Int) : AbstractButton(x, y, 20, 20, Component.empty()) {
        private val stack = ItemStack(item)
        private val id = BuiltInRegistries.ITEM.getKey(item).toString()

        init {
            message = stack.hoverName
            setTooltip(Tooltip.create(stack.hoverName))
        }

        override fun onPress() {
            PacketDistributor.sendToServer(ShopItemPayload(menu.setup.pos, id))
            show(View.SETTINGS)
        }

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val chosen = ItemStack.isSameItemSameComponents(menu.templateItem, stack)
            graphics.blitSprite(CasinoUi.SECONDARY.get(true, chosen || isHoveredOrFocused), x, y, width, height)
            graphics.renderFakeItem(stack, x + 2, y + 2)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }
}
