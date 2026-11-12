package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.account.AccountRules
import fr.bde_teknantes.lnb.economy.client.ClientAccounts
import fr.bde_teknantes.lnb.economy.network.AccountEntry
import fr.bde_teknantes.lnb.economy.network.AccountInvitePayload
import fr.bde_teknantes.lnb.economy.network.AccountMembersRequestPayload
import fr.bde_teknantes.lnb.economy.network.InvitationCancelPayload
import fr.bde_teknantes.lnb.economy.network.MemberEntry
import fr.bde_teknantes.lnb.economy.network.PendingEntry
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

/**
 * The members of a shared account, with their faces and roles, then the invitations not answered yet. Owners invite
 * from the search box: it proposes the players who are on as they type (Tab completes, Enter invites), and takes any
 * other Minecraft username too. Roles and removals stay on the site.
 */
class AccountMembersScreen(private val parent: Screen?, private val account: AccountEntry) :
    Screen(Component.translatable("gui.lnbeconomy.members.title", account.name ?: "")) {
    private companion object {
        const val PANEL_WIDTH = 268
        const val PANEL_HEIGHT = 222
        const val LEFT = 16
        const val INNER = PANEL_WIDTH - 2 * LEFT
        const val LIST_TOP = 28
        const val ROW = 16
        const val SUGGESTIONS = 3
        const val GREEN = 0xFF78E68C.toInt()
        const val RED = 0xFFFF6060.toInt()
    }

    private var search: EditBox? = null
    private var searchText = ""
    private var scroll = 0
    private var shownRevision = -1
    private var panelX = 0
    private var panelY = 0

    private val handle: String get() = account.handle.orEmpty()
    private val owner: Boolean get() = account.isOwner

    /** How many list rows fit: owners give some of the height to the invitation box. */
    private val listRows: Int get() = if (owner) 6 else 9

    private val members: List<MemberEntry> get() = ClientAccounts.members?.takeIf { it.handle == handle }?.members.orEmpty()
    private val pending: List<PendingEntry> get() = ClientAccounts.members?.takeIf { it.handle == handle }?.pending.orEmpty()
    private fun rows(): List<Any> = members + pending

    private val inviteY: Int get() = panelY + LIST_TOP + listRows * ROW + 18

    override fun init() {
        if (shownRevision < 0) PacketDistributor.sendToServer(AccountMembersRequestPayload(handle))
        shownRevision = ClientAccounts.revision
        panelX = (width - PANEL_WIDTH) / 2
        panelY = (height - PANEL_HEIGHT) / 2
        val x = panelX + LEFT

        val rows = rows()
        scroll = scroll.coerceIn(0, maxOf(0, rows.size - listRows))
        rows.drop(scroll).take(listRows).forEachIndexed { i, row ->
            if (row is PendingEntry && owner) {
                val cancel = CasinoButton.secondary(Component.literal("x")) { PacketDistributor.sendToServer(InvitationCancelPayload(handle, row.id)) }
                    .bounds(x + INNER - 16, panelY + LIST_TOP + i * ROW, 16, ROW - 1).build()
                cancel.setTooltip(Tooltip.create(Component.translatable("gui.lnbeconomy.members.cancel")))
                addRenderableWidget(cancel)
            }
        }

        search = if (!owner) null else {
            val box = CasinoUi.editBox(font, x, inviteY, INNER, Component.translatable("gui.lnbeconomy.members.search"), 16)
            box.setFilter { text -> text.all { it.isLetterOrDigit() || it == '_' } }
            box.value = searchText
            box.setResponder { text ->
                if (text == searchText) return@setResponder
                searchText = text
                ClientAccounts.clearFeedback()
                rebuildWidgets()
            }
            addRenderableWidget(box).also { setInitialFocus(it) }
        }
        if (owner) {
            suggestions().forEachIndexed { i, username ->
                addRenderableWidget(Suggestion(username, x, inviteY + 20 + i * ROW))
            }
        }

        addRenderableWidget(
            CasinoButton.secondary(CommonComponents.GUI_BACK) { onClose() }
                .bounds(width / 2 - 45, panelY + PANEL_HEIGHT - 30, 90, 18).build(),
        )
    }

    /**
     * Who the search box proposes: players who are on, not in the account yet, whose name holds what was typed; then
     * what was typed itself when it can be a username nobody on has, for a player who is away.
     */
    private fun suggestions(): List<String> {
        val taken = (members.map { it.username } + pending.map { it.username }).map(String::lowercase).toSet()
        val self = minecraft?.player?.gameProfile?.name
        val online = minecraft?.connection?.onlinePlayers.orEmpty().map { it.profile.name }
            .filter { it != self && it.lowercase() !in taken && it.contains(searchText, ignoreCase = true) }
            .sortedWith(compareBy({ !it.startsWith(searchText, ignoreCase = true) }, { it.lowercase() }))
        val typed = searchText.takeIf { text -> AccountRules.isUsername(text) && text.lowercase() !in taken && online.none { it.equals(text, ignoreCase = true) } }
        return (online + listOfNotNull(typed)).take(SUGGESTIONS)
    }

    private fun invite(username: String) {
        ClientAccounts.clearFeedback()
        PacketDistributor.sendToServer(AccountInvitePayload(handle, username))
        searchText = ""
        search?.value = ""
        rebuildWidgets()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val box = search
        if (box != null && box.isFocused) {
            val first = suggestions().firstOrNull()
            when (keyCode) {
                GLFW.GLFW_KEY_TAB -> if (first != null) {
                    box.value = first
                    return true
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> if (first != null) {
                    invite(first)
                    return true
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun tick() {
        if (ClientAccounts.revision != shownRevision) rebuildWidgets()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val before = scroll
        scroll = (scroll - scrollY.toInt()).coerceIn(0, maxOf(0, rows().size - listRows))
        if (scroll != before) rebuildWidgets()
        return true
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        CasinoUi.panel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT)
        graphics.blitSprite(CasinoUi.PANEL_INSET, panelX + LEFT - 4, panelY + LIST_TOP - 4, INNER + 8, listRows * ROW + 7)
        search?.let { CasinoUi.field(graphics, it) }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val x = panelX + LEFT
        CasinoUi.title(graphics, font, Component.literal(CasinoUi.fit(font, title.string, PANEL_WIDTH - 30)), width / 2, panelY + 10)

        val rows = rows()
        if (ClientAccounts.members?.handle != handle) {
            graphics.drawString(font, Component.translatable("gui.lnbeconomy.accounts.loading"), x, panelY + LIST_TOP + 2, CasinoUi.DIM, true)
        }
        rows.drop(scroll).take(listRows).forEachIndexed { i, row ->
            val y = panelY + LIST_TOP + i * ROW
            when (row) {
                is MemberEntry -> {
                    Heads.draw(graphics, row.username, x, y + 2, 10)
                    graphics.drawString(font, row.username, x + 15, y + 3, CasinoUi.IVORY, true)
                    val role = Component.translatable("gui.lnbeconomy.accounts.role.${row.role}")
                    graphics.drawString(font, role, x + INNER - font.width(role), y + 3, if (row.role == "owner") CasinoUi.GOLD_TEXT else CasinoUi.DIM, true)
                }
                is PendingEntry -> {
                    Heads.draw(graphics, row.username, x, y + 2, 10)
                    graphics.drawString(font, row.username, x + 15, y + 3, CasinoUi.GREY, true)
                    val invited = Component.translatable("gui.lnbeconomy.members.invited")
                    graphics.drawString(font, invited, x + INNER - font.width(invited) - if (owner) 20 else 0, y + 3, CasinoUi.GREY, true)
                }
            }
        }

        if (owner) {
            graphics.drawString(font, Component.translatable("gui.lnbeconomy.members.invite"), x, inviteY - 11, CasinoUi.GOLD_TEXT, true)
            val feedback = ClientAccounts.feedback
            if (feedback != null) {
                val line = font.split(feedback.second, INNER).firstOrNull()
                if (line != null) graphics.drawString(font, line, x, inviteY + 20 + SUGGESTIONS * ROW + 2, if (feedback.first) GREEN else RED, true)
            }
        }
    }

    override fun onClose() {
        ClientAccounts.clearFeedback()
        minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false

    /** One player the search proposes: face and name. A click invites them. */
    private inner class Suggestion(private val username: String, x: Int, y: Int) :
        AbstractButton(x, y, INNER, ROW - 1, Component.literal(username)) {

        private val online = minecraft?.connection?.onlinePlayers.orEmpty().any { it.profile.name == username }

        override fun onPress() = invite(username)

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            graphics.blitSprite(CasinoUi.SECONDARY.get(true, isHoveredOrFocused), x, y, width, height)
            Heads.draw(graphics, username, x + 3, y + 2, 10)
            graphics.drawString(font, username, x + 17, y + 3, CasinoUi.IVORY, true)
            val hint = Component.translatable(if (online) "gui.lnbeconomy.members.invite.online" else "gui.lnbeconomy.members.invite.away")
            graphics.drawString(font, hint, x + width - font.width(hint) - 5, y + 3, CasinoUi.DIM, true)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }
}
