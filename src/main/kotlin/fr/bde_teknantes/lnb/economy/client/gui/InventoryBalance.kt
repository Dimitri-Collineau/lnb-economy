package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.client.ClientEconomy
import fr.bde_teknantes.lnb.economy.money.Amounts
import fr.bde_teknantes.lnb.economy.network.EconomyStatus
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.client.event.ScreenEvent

/**
 * The balance of the default account above the survival inventory, top right, with a "My accounts" button on its
 * right. Hidden for players without an economy. Follows the inventory when the recipe book moves it.
 */
object InventoryBalance {
    private const val BUTTON_HEIGHT = 12
    private const val GAP = 4

    private var button: Button? = null

    fun onInit(event: ScreenEvent.Init.Post) {
        val screen = event.screen as? InventoryScreen ?: return
        button = null
        if (!visible()) return
        ClientEconomy.requestRefresh(always = false)
        val label = Component.translatable("gui.lnbeconomy.inventory.accounts")
        val width = Minecraft.getInstance().font.width(label) + 10
        val created = Button.builder(label) { Minecraft.getInstance().setScreen(AccountsScreen(screen)) }
            .bounds(0, 0, width, BUTTON_HEIGHT)
            .build()
        place(screen, created)
        event.addListener(created)
        button = created
    }

    fun onRenderPre(event: ScreenEvent.Render.Pre) {
        val screen = event.screen as? InventoryScreen ?: return
        val button = button ?: return
        button.visible = visible()
        place(screen, button)
    }

    fun onRenderPost(event: ScreenEvent.Render.Post) {
        val screen = event.screen as? InventoryScreen ?: return
        val button = button ?: return
        if (!button.visible) return
        val account = ClientEconomy.snapshot.defaultAccount ?: return
        val minecraft = Minecraft.getInstance()
        val text = Amounts.compact(account.balance, minecraft.options.languageCode)
        val font = minecraft.font
        val x = button.x - GAP - font.width(text)
        val y = button.y + (BUTTON_HEIGHT - font.lineHeight) / 2 + 1
        event.guiGraphics.drawString(font, text, x, y, 0xFFFFD45A.toInt(), true)

        val hovered = event.mouseX in x until button.x && event.mouseY in button.y until button.y + BUTTON_HEIGHT
        if (hovered) {
            val name = account.handle?.let { Component.literal(account.name ?: "#$it") }
                ?: Component.translatable("gui.lnbeconomy.accounts.personal")
            event.guiGraphics.renderTooltip(font, Component.translatable("gui.lnbeconomy.inventory.default", name), event.mouseX, event.mouseY)
        }
    }

    private fun visible(): Boolean {
        val snapshot = ClientEconomy.snapshot
        return snapshot.status == EconomyStatus.READY && snapshot.defaultAccount != null
    }

    private fun place(screen: InventoryScreen, button: Button) {
        button.x = screen.guiLeft + screen.xSize - button.width
        button.y = screen.guiTop - BUTTON_HEIGHT - 2
        if (button.tooltip == null) button.setTooltip(Tooltip.create(Component.translatable("gui.lnbeconomy.inventory.accounts.hint")))
    }
}
