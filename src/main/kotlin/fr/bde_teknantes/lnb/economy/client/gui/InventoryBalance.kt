package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.client.ClientEconomy
import fr.bde_teknantes.lnb.economy.network.EconomyStatus
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.client.event.ScreenEvent

/**
 * The balance of the default account, on a felt plaque bracketed to the top right of the survival inventory.
 * The account icon on the plaque opens "My accounts". Hidden for players without an economy. Follows the
 * inventory when the recipe book moves it.
 */
object InventoryBalance {
    /** The plaque rises this far above the inventory and comes down this far over it. */
    private const val ABOVE = 13
    private const val OVER = 5
    private const val INSET_RIGHT = 8
    private const val TEXT_LEFT = 6
    private const val BUTTON_WIDTH = 15
    private const val BUTTON_HEIGHT = 14
    private const val SHADOW = 0x55000000

    private var plaque: Plaque? = null

    fun onInit(event: ScreenEvent.Init.Post) {
        val screen = event.screen as? InventoryScreen ?: return
        plaque = null
        if (!visible()) return
        ClientEconomy.requestRefresh(always = false)
        val created = Plaque { Minecraft.getInstance().setScreen(AccountsScreen(screen)) }
        created.setTooltip(Tooltip.create(Component.translatable("gui.lnbeconomy.inventory.accounts.hint")))
        place(screen, created)
        event.addListener(created)
        plaque = created
    }

    fun onRenderPre(event: ScreenEvent.Render.Pre) {
        val screen = event.screen as? InventoryScreen ?: return
        val plaque = plaque ?: return
        plaque.visible = visible()
        place(screen, plaque)
    }

    fun onRenderPost(event: ScreenEvent.Render.Post) {
        event.screen as? InventoryScreen ?: return
        val plaque = plaque ?: return
        if (!plaque.visible) return
        val account = ClientEconomy.snapshot.defaultAccount ?: return
        val inBalance = event.mouseX in plaque.left until plaque.x &&
            event.mouseY in plaque.containerTop - ABOVE until plaque.containerTop + OVER
        if (inBalance) {
            val name = account.handle?.let { Component.literal(account.name ?: "#$it") }
                ?: Component.translatable("gui.lnbeconomy.accounts.personal")
            val font = Minecraft.getInstance().font
            event.guiGraphics.renderTooltip(font, Component.translatable("gui.lnbeconomy.inventory.default", name), event.mouseX, event.mouseY)
        }
    }

    private fun visible(): Boolean {
        val snapshot = ClientEconomy.snapshot
        return snapshot.status == EconomyStatus.READY && snapshot.defaultAccount != null
    }

    private fun place(screen: InventoryScreen, plaque: Plaque) {
        plaque.containerTop = screen.guiTop
        val right = screen.guiLeft + screen.xSize - INSET_RIGHT
        plaque.x = right - 3 - BUTTON_WIDTH
        plaque.y = screen.guiTop - ABOVE + 2
    }

    /**
     * The whole plaque. Its own bounds are the account button, the only clickable part; the rest of the plaque,
     * the balance and the brackets are drawn around it, mostly to its left.
     */
    private class Plaque(private val onPress: () -> Unit) :
        AbstractButton(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("gui.lnbeconomy.inventory.accounts")) {

        var containerTop = 0
        var left = 0
            private set

        override fun onPress() = onPress.invoke()

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val account = ClientEconomy.snapshot.defaultAccount ?: return
            val minecraft = Minecraft.getInstance()
            val font = minecraft.font
            val text = Lnbit.compact(account.balance, minecraft.options.languageCode)
            val right = x + BUTTON_WIDTH + 3
            val width = TEXT_LEFT + font.width(text) + 5 + BUTTON_WIDTH + 3
            left = right - width
            val top = containerTop - ABOVE
            val bottom = containerTop + OVER

            graphics.blitSprite(CasinoUi.PLAQUE, left, top, width, ABOVE + OVER)
            graphics.fill(left + 1, bottom, right + 1, bottom + 1, SHADOW)
            graphics.fill(right, containerTop, right + 1, bottom, SHADOW)
            graphics.drawString(font, text, left + TEXT_LEFT, top + 5, CasinoUi.GOLD_TEXT, true)

            graphics.blitSprite(CasinoUi.SECONDARY.get(true, isHoveredOrFocused), x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
            graphics.blitSprite(if (account.isPersonal) CasinoUi.ACCOUNT_PERSONAL else CasinoUi.ACCOUNT_SHARED, x + 3, y + 3, 9, 9)

            val bracketTop = containerTop - CasinoUi.BRACKET_ABOVE
            graphics.blitSprite(CasinoUi.BRACKET_LEFT, left - 6, bracketTop, CasinoUi.BRACKET_WIDTH, CasinoUi.BRACKET_HEIGHT)
            graphics.blitSprite(CasinoUi.BRACKET_RIGHT, right - 1, bracketTop, CasinoUi.BRACKET_WIDTH, CasinoUi.BRACKET_HEIGHT)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }
}
