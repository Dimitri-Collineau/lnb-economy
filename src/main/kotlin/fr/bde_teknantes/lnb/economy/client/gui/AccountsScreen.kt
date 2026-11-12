package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.client.ClientAccounts
import fr.bde_teknantes.lnb.economy.client.ClientEconomy
import fr.bde_teknantes.lnb.economy.network.AccountEntry
import fr.bde_teknantes.lnb.economy.network.EconomyStatus
import fr.bde_teknantes.lnb.economy.network.InvitationAnswerPayload
import fr.bde_teknantes.lnb.economy.network.InvitationEntry
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
 * "My accounts": the invitations waiting for an answer, then every account of the player with its balance, the one
 * that pays by default starred. Clicking an account the player can spend from makes it the default; the button at the
 * end of a shared account opens its settings and members; "New" opens a shared account. The history is on the site.
 */
class AccountsScreen(private val parent: Screen?) : Screen(Component.translatable("gui.lnbeconomy.accounts.title")) {
    private companion object {
        const val PANEL_WIDTH = 292
        const val PANEL_MAX_HEIGHT = 222
        const val ROW_WIDTH = 256
        const val ROW_HEIGHT = 20
        const val ROW_GAP = 3
        const val ROWS_TOP = 42
        const val FOOTER = 40
        const val SIDE_BUTTON = 20
        const val BANK_URL = "https://lnb.bde-teknantes.fr/minecraft/bank"
    }

    private var shownRevision = -1
    private var scroll = 0
    private var panelX = 0
    private var panelY = 0
    private var panelHeight = 0

    /** Invitations first, then accounts: one list, scrolled together. */
    private fun rows(): List<Any> = ClientEconomy.snapshot.invitations + ClientEconomy.snapshot.accounts

    override fun init() {
        ClientEconomy.requestRefresh(always = true)
        shownRevision = ClientEconomy.revision
        panelHeight = minOf(PANEL_MAX_HEIGHT, height - 16)
        panelX = (width - PANEL_WIDTH) / 2
        panelY = (height - panelHeight) / 2
        val rowX = (width - ROW_WIDTH) / 2

        val rows = rows()
        val visible = visibleRows()
        scroll = scroll.coerceIn(0, maxOf(0, rows.size - visible))
        rows.drop(scroll).take(visible).forEachIndexed { index, row ->
            val y = panelY + ROWS_TOP + index * (ROW_HEIGHT + ROW_GAP)
            when (row) {
                is InvitationEntry -> addInvitation(row, rowX, y)
                is AccountEntry -> addAccount(row, rowX, y)
            }
        }

        val footerY = panelY + panelHeight - 34
        addRenderableWidget(
            CasinoButton.secondary(Component.translatable("gui.lnbeconomy.accounts.site")) {
                minecraft?.setScreen(ConfirmLinkScreen({ open ->
                    if (open) Util.getPlatform().openUri(BANK_URL)
                    minecraft?.setScreen(this)
                }, BANK_URL, true))
            }.bounds(rowX, footerY, 122, 20).build(),
        )
        val create = addRenderableWidget(
            CasinoButton.secondary(Component.translatable("gui.lnbeconomy.accounts.new")) {
                minecraft?.setScreen(AccountEditScreen(this, null))
            }.bounds(rowX + 126, footerY, 70, 20).build(),
        )
        create.active = ClientEconomy.snapshot.status == EconomyStatus.READY
        create.setTooltip(Tooltip.create(Component.translatable("gui.lnbeconomy.accounts.new.hint")))
        addRenderableWidget(
            CasinoButton.primary(CommonComponents.GUI_DONE) { onClose() }
                .bounds(rowX + 200, footerY, ROW_WIDTH - 200, 20).build(),
        )
    }

    private fun addAccount(account: AccountEntry, x: Int, y: Int) {
        val width = if (account.isPersonal) ROW_WIDTH else ROW_WIDTH - SIDE_BUTTON - 2
        val row = AccountRow(account, x, y, width)
        row.setTooltip(Tooltip.create(hint(account)))
        row.active = account.canSpend && !isDefault(account)
        addRenderableWidget(row)
        if (account.isPersonal) return
        val manage = CasinoButton.secondary(Component.literal("...")) { minecraft?.setScreen(AccountEditScreen(this, account)) }
            .bounds(x + width + 2, y, SIDE_BUTTON, ROW_HEIGHT).build()
        manage.setTooltip(Tooltip.create(Component.translatable(if (account.isOwner) "gui.lnbeconomy.accounts.manage" else "gui.lnbeconomy.accounts.members")))
        addRenderableWidget(manage)
    }

    private fun addInvitation(invitation: InvitationEntry, x: Int, y: Int) {
        val width = ROW_WIDTH - 2 * (SIDE_BUTTON + 2)
        addRenderableWidget(InvitationRow(invitation, x, y, width))
        val accept = CasinoButton.primary(Component.literal("+")) { PacketDistributor.sendToServer(InvitationAnswerPayload(invitation.id, true)) }
            .bounds(x + width + 2, y, SIDE_BUTTON, ROW_HEIGHT).build()
        accept.setTooltip(Tooltip.create(Component.translatable("gui.lnbeconomy.accounts.invitation.accept")))
        addRenderableWidget(accept)
        val decline = CasinoButton.secondary(Component.literal("x")) { PacketDistributor.sendToServer(InvitationAnswerPayload(invitation.id, false)) }
            .bounds(x + width + SIDE_BUTTON + 4, y, SIDE_BUTTON, ROW_HEIGHT).build()
        decline.setTooltip(Tooltip.create(Component.translatable("gui.lnbeconomy.accounts.invitation.decline")))
        addRenderableWidget(decline)
    }

    private fun visibleRows(): Int = maxOf(1, (panelHeight - ROWS_TOP - FOOTER) / (ROW_HEIGHT + ROW_GAP))

    private fun isDefault(account: AccountEntry) = account.id == ClientEconomy.snapshot.defaultAccountId

    override fun tick() {
        if (ClientEconomy.revision != shownRevision) rebuildWidgets()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val before = scroll
        scroll = (scroll - scrollY.toInt()).coerceIn(0, maxOf(0, rows().size - visibleRows()))
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
        ClientAccounts.clearFeedback()
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
        val text = Component.empty().append(role)
        account.type?.let { text.append(" · ").append(Component.translatable("gui.lnbeconomy.accounts.type.$it")) }
        return text.append("\n").append(Component.literal("").append(action).withStyle(ChatFormatting.GRAY))
    }

    /** One account: icon, name, frozen tag, balance, and a star on the default one. A click makes it the default. */
    private inner class AccountRow(private val account: AccountEntry, x: Int, y: Int, width: Int) :
        AbstractButton(x, y, width, ROW_HEIGHT, Component.empty()) {

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

            val language = minecraft?.options?.languageCode ?: "en_us"
            val amount = Lnbit.compact(account.balance, language)
            val amountX = x + width - 22 - font.width(amount)
            val readOnly = !account.canSpend && !account.frozen && !isDefault
            val frozen = if (account.frozen) Component.translatable("economy.lnbeconomy.balance.frozen") else null
            val room = amountX - (x + 20) - 6 - (frozen?.let(font::width) ?: 0)
            val name = if (account.isPersonal) Component.translatable("gui.lnbeconomy.accounts.personal").string
            else CasinoUi.fit(font, "${account.name} (#${account.handle})", room)
            val nameEnd = graphics.drawString(font, name, x + 20, y + 6, if (readOnly) CasinoUi.GREY else CasinoUi.IVORY, true)
            if (frozen != null) graphics.drawString(font, frozen, nameEnd, y + 6, 0xFF55FFFF.toInt(), true)

            graphics.drawString(font, amount, amountX, y + 6, if (readOnly) CasinoUi.GREY else CasinoUi.GOLD_TEXT, true)
            when {
                isDefault -> graphics.blitSprite(CasinoUi.STAR, x + width - 15, y + 5, 9, 9)
                active && isHoveredOrFocused -> graphics.blitSprite(CasinoUi.STAR_EMPTY, x + width - 15, y + 5, 9, 9)
            }
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }

    /** One invitation: who invites, with their face, and to which account. Answered with the two buttons beside it. */
    private inner class InvitationRow(private val invitation: InvitationEntry, x: Int, y: Int, width: Int) :
        AbstractButton(x, y, width, ROW_HEIGHT, Component.empty()) {

        init {
            active = false
            setTooltip(Tooltip.create(Component.translatable("gui.lnbeconomy.accounts.invitation.hint", invitation.invitedBy, invitation.name, "#${invitation.handle}")))
        }

        override fun onPress() = Unit

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            graphics.blitSprite(CasinoUi.PANEL_INSET, x, y, width, height)
            if (invitation.invitedBy.isNotEmpty()) Heads.draw(graphics, invitation.invitedBy, x + 5, y + 5, 10)
            val text = Component.translatable("gui.lnbeconomy.accounts.invitation", invitation.name).string
            graphics.drawString(font, CasinoUi.fit(font, text, width - 26), x + 20, y + 6, CasinoUi.GOLD_TEXT, true)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }
}
