package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.client.ClientEconomy
import fr.bde_teknantes.lnb.economy.network.AccountEntry
import fr.bde_teknantes.lnb.economy.network.EconomyStatus
import fr.bde_teknantes.lnb.economy.network.SetDefaultAccountPayload
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

/**
 * "My accounts": every account of the player with its balance, the one that pays by default starred.
 * Clicking an account the player can spend from makes it the default. The history is on the site.
 */
class AccountsScreen(private val parent: Screen?) : Screen(Component.translatable("gui.lnbeconomy.accounts.title")) {
    private companion object {
        const val PANEL_WIDTH = 292
        const val PANEL_MAX_HEIGHT = 200
        const val ROW_WIDTH = 256
        const val ROW_HEIGHT = 20
        const val ROW_GAP = 3
        const val ROWS_TOP = 42
        const val FOOTER = 40
        const val BANK_URL = "https://lnb.bde-teknantes.fr/minecraft/bank"
    }

    private var shownRevision = -1
    private var scroll = 0
    private var panelX = 0
    private var panelY = 0
    private var panelHeight = 0

    override fun init() {
        ClientEconomy.requestRefresh(always = true)
        shownRevision = ClientEconomy.revision
        panelHeight = minOf(PANEL_MAX_HEIGHT, height - 16)
        panelX = (width - PANEL_WIDTH) / 2
        panelY = (height - panelHeight) / 2
        val rowX = (width - ROW_WIDTH) / 2

        val accounts = ClientEconomy.snapshot.accounts
        val visible = visibleRows()
        scroll = scroll.coerceIn(0, maxOf(0, accounts.size - visible))
        accounts.drop(scroll).take(visible).forEachIndexed { index, account ->
            val row = AccountRow(account, rowX, panelY + ROWS_TOP + index * (ROW_HEIGHT + ROW_GAP))
            row.setTooltip(Tooltip.create(hint(account)))
            row.active = account.canSpend && !isDefault(account)
            addRenderableWidget(row)
        }

        val footerY = panelY + panelHeight - 34
        addRenderableWidget(
            CasinoButton.secondary(Component.translatable("gui.lnbeconomy.accounts.site")) {
                minecraft?.setScreen(ConfirmLinkScreen({ open ->
                    if (open) Util.getPlatform().openUri(BANK_URL)
                    minecraft?.setScreen(this)
                }, BANK_URL, true))
            }.bounds(rowX, footerY, 150, 20).build(),
        )
        addRenderableWidget(
            CasinoButton.primary(CommonComponents.GUI_DONE) { onClose() }
                .bounds(rowX + ROW_WIDTH - 100, footerY, 100, 20).build(),
        )
    }

    private fun visibleRows(): Int = maxOf(1, (panelHeight - ROWS_TOP - FOOTER) / (ROW_HEIGHT + ROW_GAP))

    private fun isDefault(account: AccountEntry) = account.id == ClientEconomy.snapshot.defaultAccountId

    override fun tick() {
        if (ClientEconomy.revision != shownRevision) rebuildWidgets()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val before = scroll
        scroll = (scroll - scrollY.toInt()).coerceIn(0, maxOf(0, ClientEconomy.snapshot.accounts.size - visibleRows()))
        if (scroll != before) rebuildWidgets()
        return true
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        CasinoUi.panel(graphics, panelX, panelY, PANEL_WIDTH, panelHeight)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        CasinoUi.title(graphics, font, title, width / 2, panelY + 12)
        val status = ClientEconomy.snapshot.status
        val message = when {
            status == EconomyStatus.LOADING -> Component.translatable("gui.lnbeconomy.accounts.loading")
            status == EconomyStatus.NOT_LINKED -> Component.translatable("economy.lnbeconomy.problem.notLinked")
            status == EconomyStatus.NOT_ELIGIBLE -> Component.translatable("economy.lnbeconomy.problem.notEligible")
            ClientEconomy.snapshot.accounts.isEmpty() -> Component.translatable("economy.lnbeconomy.problem.unavailable")
            else -> Component.translatable("gui.lnbeconomy.accounts.hint")
        }
        graphics.drawCenteredString(font, message, width / 2, panelY + 26, CasinoUi.DIM)
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    private fun hint(account: AccountEntry): Component {
        val role = Component.translatable("gui.lnbeconomy.accounts.role.${account.role}")
        val action = when {
            account.frozen -> Component.translatable("gui.lnbeconomy.accounts.frozen")
            isDefault(account) -> Component.translatable("gui.lnbeconomy.accounts.is_default")
            account.canSpend -> Component.translatable("gui.lnbeconomy.accounts.make_default")
            else -> Component.translatable("gui.lnbeconomy.accounts.viewer")
        }
        return Component.empty().append(role).append("\n").append(Component.literal("").append(action).withStyle(ChatFormatting.GRAY))
    }

    /** One account: icon, name, frozen tag, balance, and a star on the default one. A click makes it the default. */
    private inner class AccountRow(private val account: AccountEntry, x: Int, y: Int) :
        AbstractButton(x, y, ROW_WIDTH, ROW_HEIGHT, Component.empty()) {

        override fun onPress() {
            PacketDistributor.sendToServer(SetDefaultAccountPayload(account.id))
        }

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val isDefault = isDefault(account)
            val sprites = CasinoUi.SECONDARY
            val sprite = when {
                isDefault -> sprites.highlighted
                account.frozen -> sprites.normal
                !active -> sprites.disabled
                isHoveredOrFocused -> sprites.highlighted
                else -> sprites.normal
            }
            graphics.blitSprite(sprite, x, y, width, height)
            graphics.blitSprite(if (account.isPersonal) CasinoUi.ACCOUNT_PERSONAL else CasinoUi.ACCOUNT_SHARED, x + 6, y + 6, 9, 9)

            val readOnly = !account.canSpend && !account.frozen && !isDefault
            val name = if (account.isPersonal) Component.translatable("gui.lnbeconomy.accounts.personal")
            else Component.literal("${account.name} (#${account.handle})")
            val nameEnd = graphics.drawString(font, name, x + 20, y + 6, if (readOnly) CasinoUi.GREY else CasinoUi.IVORY, true)
            if (account.frozen) {
                graphics.drawString(font, Component.translatable("economy.lnbeconomy.balance.frozen"), nameEnd, y + 6, 0xFF55FFFF.toInt(), true)
            }

            val language = minecraft?.options?.languageCode ?: "en_us"
            val amount = Lnbit.compact(account.balance, language)
            graphics.drawString(font, amount, x + width - 22 - font.width(amount), y + 6, if (readOnly) CasinoUi.GREY else CasinoUi.GOLD_TEXT, true)
            when {
                isDefault -> graphics.blitSprite(CasinoUi.STAR, x + width - 15, y + 5, 9, 9)
                active && isHoveredOrFocused -> graphics.blitSprite(CasinoUi.STAR_EMPTY, x + width - 15, y + 5, 9, 9)
            }
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }
}
