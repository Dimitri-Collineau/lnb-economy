package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.account.AccountRules
import fr.bde_teknantes.lnb.economy.account.HandleSuggestion
import fr.bde_teknantes.lnb.economy.api.AccountTypes
import fr.bde_teknantes.lnb.economy.client.ClientAccounts
import fr.bde_teknantes.lnb.economy.client.ClientEconomy
import fr.bde_teknantes.lnb.economy.network.AccountCreatePayload
import fr.bde_teknantes.lnb.economy.network.AccountEditPayload
import fr.bde_teknantes.lnb.economy.network.AccountEntry
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

/**
 * Opens a shared account ([existing] null) or manages one: its name, and its type among team, shop and casino. A
 * casino needs a free casino licence and stays a casino. Only owners change anything; everyone gets to the members.
 * The handle is proposed from the name and never changes once the account is open.
 */
class AccountEditScreen(private val parent: Screen?, private val existing: AccountEntry?) : Screen(
    if (existing == null) Component.translatable("gui.lnbeconomy.account.new.title")
    else Component.translatable("gui.lnbeconomy.account.manage.title", existing.name ?: ""),
) {
    private companion object {
        const val PANEL_WIDTH = 268
        const val PANEL_HEIGHT = 206
        const val LEFT = 20
        const val INNER = PANEL_WIDTH - 2 * LEFT
        const val NAME_Y = 40
        const val HANDLE_Y = 76
        const val TYPE_Y = 112
        const val FEEDBACK_Y = 140
        const val BUTTONS_Y = 168
        const val GREEN = 0xFF78E68C.toInt()
        const val RED = 0xFFFF6060.toInt()
    }

    private lateinit var name: EditBox
    private var handle: EditBox? = null
    private lateinit var save: Button
    private var nameText = existing?.name.orEmpty()
    private var handleText = ""

    /** Until the player types a handle themselves, it follows the name. */
    private var handleTouched = false
    private var type = existing?.type ?: AccountTypes.TEAM
    private var shownRevision = -1
    private var sent = false
    private var panelX = 0
    private var panelY = 0

    /** The account as the server last sent it: its name or type may have just changed. */
    private val account: AccountEntry? get() = existing?.let { old -> ClientEconomy.snapshot.accounts.firstOrNull { it.id == old.id } ?: old }
    private val mayEdit: Boolean get() = existing == null || account?.isOwner == true
    private val wasCasino: Boolean get() = account?.type == AccountTypes.CASINO

    override fun init() {
        shownRevision = ClientAccounts.revision + ClientEconomy.revision
        panelX = (width - PANEL_WIDTH) / 2
        panelY = (height - PANEL_HEIGHT) / 2
        val x = panelX + LEFT

        name = CasinoUi.editBox(font, x, panelY + NAME_Y, INNER, Component.translatable("gui.lnbeconomy.account.name.hint"), AccountRules.MAX_NAME)
        name.value = nameText
        name.setEditable(mayEdit)
        name.setResponder { text ->
            nameText = text
            if (existing == null && !handleTouched) handle?.let { it.value = HandleSuggestion.of(text) }
            updateSave()
        }
        addRenderableWidget(name)

        handle = if (existing != null) null else {
            val box = CasinoUi.editBox(font, x + 10, panelY + HANDLE_Y, INNER - 10, Component.translatable("gui.lnbeconomy.account.handle.hint"), 24)
            box.setFilter { text -> text.all { it in 'a'..'z' || it in '0'..'9' || it == '-' } }
            box.value = handleText
            box.setResponder { text ->
                if (text != HandleSuggestion.of(nameText)) handleTouched = true
                handleText = text
                updateSave()
            }
            addRenderableWidget(box)
        }

        val third = (INNER - 8) / 3
        AccountTypes.ALL.forEachIndexed { i, choice -> addRenderableWidget(TypeChoice(choice, x + i * (third + 4), panelY + TYPE_Y, third)) }

        val buttonsY = panelY + BUTTONS_Y
        if (existing == null) {
            save = addRenderableWidget(CasinoButton.primary(Component.translatable("gui.lnbeconomy.account.create")) { submit() }.bounds(x, buttonsY, 110, 20).build())
            addRenderableWidget(CasinoButton.secondary(CommonComponents.GUI_BACK) { onClose() }.bounds(x + INNER - 110, buttonsY, 110, 20).build())
        } else {
            val width = (INNER - 8) / 3
            save = addRenderableWidget(CasinoButton.primary(Component.translatable("gui.lnbeconomy.shop.save")) { submit() }.bounds(x, buttonsY, width, 20).build())
            addRenderableWidget(
                CasinoButton.secondary(Component.translatable("gui.lnbeconomy.accounts.members")) {
                    minecraft?.setScreen(AccountMembersScreen(this, account ?: existing))
                }.bounds(x + width + 4, buttonsY, width, 20).build(),
            )
            addRenderableWidget(CasinoButton.secondary(CommonComponents.GUI_BACK) { onClose() }.bounds(x + 2 * (width + 4), buttonsY, width, 20).build())
        }
        setInitialFocus(name)
        updateSave()
    }

    private fun updateSave() {
        if (!::save.isInitialized) return
        val nameOk = AccountRules.nameProblem(nameText) == null
        save.active = mayEdit && nameOk && if (existing == null) {
            AccountRules.handleProblem(handleText) == null
        } else {
            nameText.trim() != account?.name || type != (account?.type ?: AccountTypes.TEAM)
        }
    }

    private fun submit() {
        ClientAccounts.clearFeedback()
        sent = true
        val current = account
        if (current == null) {
            PacketDistributor.sendToServer(AccountCreatePayload(handleText, nameText.trim(), type))
        } else {
            val newName = nameText.trim().takeIf { it != current.name }
            val newType = type.takeIf { it != (current.type ?: AccountTypes.TEAM) }
            PacketDistributor.sendToServer(AccountEditPayload(current.handle ?: return, newName, newType))
        }
    }

    override fun tick() {
        val revision = ClientAccounts.revision + ClientEconomy.revision
        if (revision == shownRevision) return
        shownRevision = revision
        // A new account that went through has nothing more to ask here.
        if (existing == null && sent && ClientAccounts.feedback?.first == true) return onClose()
        if (ClientAccounts.feedback != null) sent = false
        account?.type?.let { if (ClientAccounts.feedback?.first == true) type = it }
        updateSave()
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        CasinoUi.panel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT)
        CasinoUi.field(graphics, name)
        handle?.let { CasinoUi.field(graphics, it) }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val x = panelX + LEFT
        CasinoUi.title(graphics, font, Component.literal(CasinoUi.fit(font, title.string, PANEL_WIDTH - 30)), width / 2, panelY + 12)
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.account.name"), x, panelY + NAME_Y - 11, CasinoUi.GOLD_TEXT, true)
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.account.handle"), x, panelY + HANDLE_Y - 11, CasinoUi.GOLD_TEXT, true)
        if (existing == null) {
            graphics.drawString(font, "#", x + 1, panelY + HANDLE_Y + 5, CasinoUi.DIM, true)
        } else {
            val handle = "#${existing.handle}"
            graphics.drawString(font, handle, x, panelY + HANDLE_Y + 5, CasinoUi.IVORY, true)
            val never = Component.translatable("gui.lnbeconomy.account.handle.fixed")
            if (font.width(handle) + 8 + font.width(never) <= INNER) {
                graphics.drawString(font, never, x + INNER - font.width(never), panelY + HANDLE_Y + 5, CasinoUi.DIM, true)
            }
        }
        graphics.drawString(font, Component.translatable("gui.lnbeconomy.account.type"), x, panelY + TYPE_Y - 11, CasinoUi.GOLD_TEXT, true)
        val licences = Component.translatable("gui.lnbeconomy.account.licences", ClientEconomy.snapshot.licensesFree)
        graphics.drawString(font, licences, x + INNER - font.width(licences), panelY + TYPE_Y - 11, CasinoUi.DIM, true)

        val feedback = ClientAccounts.feedback
        val line = when {
            feedback != null -> feedback.second to if (feedback.first) GREEN else RED
            !mayEdit -> Component.translatable("gui.lnbeconomy.account.owners_only") to CasinoUi.DIM
            else -> Component.translatable("gui.lnbeconomy.account.type.$type.hint") to CasinoUi.DIM
        }
        val lines = CasinoUi.lines(font, line.first, INNER, 2)
        lines.forEachIndexed { i, text -> graphics.drawString(font, text, x, panelY + FEEDBACK_Y + i * 10, line.second, true) }
    }

    override fun onClose() {
        ClientAccounts.clearFeedback()
        minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false

    /** One account type. Casino is locked without a free licence; a casino's other types are locked for good. */
    private inner class TypeChoice(private val choice: String, x: Int, y: Int, width: Int) :
        AbstractButton(x, y, width, 20, Component.translatable("gui.lnbeconomy.accounts.type.$choice")) {

        private fun locked(): Component? = when {
            !mayEdit -> Component.translatable("gui.lnbeconomy.account.owners_only")
            wasCasino && choice != AccountTypes.CASINO -> Component.translatable("gui.lnbeconomy.account.type.locked")
            choice == AccountTypes.CASINO && !wasCasino && ClientEconomy.snapshot.licensesFree < 1 -> Component.translatable("gui.lnbeconomy.account.type.no_licence")
            else -> null
        }

        override fun onPress() {
            type = choice
            ClientAccounts.clearFeedback()
            updateSave()
        }

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val locked = locked()
            active = locked == null
            setTooltip(locked?.let(Tooltip::create))
            graphics.blitSprite(CasinoUi.SECONDARY.get(active || type == choice, type == choice || (active && isHoveredOrFocused)), x, y, width, height)
            val color = if (type == choice) CasinoUi.GOLD_TEXT else if (active) CasinoUi.IVORY else CasinoUi.GREY
            graphics.drawCenteredString(font, message, x + width / 2, y + 6, color)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }
}
