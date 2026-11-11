package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.client.ClientEconomy
import fr.bde_teknantes.lnb.economy.network.AccountEntry
import fr.bde_teknantes.lnb.economy.network.EconomyStatus
import fr.bde_teknantes.lnb.economy.network.SetDefaultAccountPayload
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
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
        const val ROW_WIDTH = 260
        const val ROW_HEIGHT = 20
        const val ROW_GAP = 2
        const val TOP = 40
        const val BANK_URL = "https://lnb.bde-teknantes.fr/minecraft/bank"
    }

    private var shownRevision = -1
    private var scroll = 0
    private val rows = mutableListOf<Button>()

    override fun init() {
        ClientEconomy.requestRefresh(always = true)
        shownRevision = ClientEconomy.revision
        rows.clear()

        val accounts = ClientEconomy.snapshot.accounts
        val visible = visibleRows()
        scroll = scroll.coerceIn(0, maxOf(0, accounts.size - visible))
        accounts.drop(scroll).take(visible).forEachIndexed { index, account ->
            val row = Button.builder(label(account)) { choose(account) }
                .bounds((width - ROW_WIDTH) / 2, TOP + index * (ROW_HEIGHT + ROW_GAP), ROW_WIDTH, ROW_HEIGHT)
                .tooltip(Tooltip.create(hint(account)))
                .build()
            row.active = account.canSpend && account.id != ClientEconomy.snapshot.defaultAccountId
            rows.add(addRenderableWidget(row))
        }

        val bottom = height - 28
        addRenderableWidget(
            Button.builder(Component.translatable("gui.lnbeconomy.accounts.site")) {
                minecraft?.setScreen(ConfirmLinkScreen({ open ->
                    if (open) Util.getPlatform().openUri(BANK_URL)
                    minecraft?.setScreen(this)
                }, BANK_URL, true))
            }.bounds(width / 2 - 154, bottom, 150, 20).build(),
        )
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE) { onClose() }.bounds(width / 2 + 4, bottom, 150, 20).build())
    }

    private fun visibleRows(): Int = maxOf(1, (height - TOP - 40) / (ROW_HEIGHT + ROW_GAP))

    override fun tick() {
        if (ClientEconomy.revision != shownRevision) rebuildWidgets()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val before = scroll
        scroll = (scroll - scrollY.toInt()).coerceIn(0, maxOf(0, ClientEconomy.snapshot.accounts.size - visibleRows()))
        if (scroll != before) rebuildWidgets()
        return true
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF)
        val status = ClientEconomy.snapshot.status
        val message = when {
            status == EconomyStatus.LOADING -> Component.translatable("gui.lnbeconomy.accounts.loading")
            status == EconomyStatus.NOT_LINKED -> Component.translatable("economy.lnbeconomy.problem.notLinked")
            status == EconomyStatus.NOT_ELIGIBLE -> Component.translatable("economy.lnbeconomy.problem.notEligible")
            ClientEconomy.snapshot.accounts.isEmpty() -> Component.translatable("economy.lnbeconomy.problem.unavailable")
            else -> Component.translatable("gui.lnbeconomy.accounts.hint")
        }
        graphics.drawCenteredString(font, message, width / 2, 26, 0xA0A0A0)
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    private fun choose(account: AccountEntry) {
        PacketDistributor.sendToServer(SetDefaultAccountPayload(account.id))
    }

    private fun label(account: AccountEntry): Component {
        val language = minecraft?.options?.languageCode ?: "en_us"
        val star = if (account.id == ClientEconomy.snapshot.defaultAccountId) "★ " else ""
        val name = if (account.isPersonal) Component.translatable("gui.lnbeconomy.accounts.personal").string
        else "${account.name} (#${account.handle})"
        val line = Component.literal("$star$name  ").append(
            Lnbit.compact(account.balance, language).withStyle(ChatFormatting.GOLD),
        )
        if (account.frozen) line.append(Component.translatable("economy.lnbeconomy.balance.frozen").withStyle(ChatFormatting.AQUA))
        return line
    }

    private fun hint(account: AccountEntry): Component {
        val role = Component.translatable("gui.lnbeconomy.accounts.role.${account.role}")
        val action = when {
            account.frozen -> Component.translatable("gui.lnbeconomy.accounts.frozen")
            account.id == ClientEconomy.snapshot.defaultAccountId -> Component.translatable("gui.lnbeconomy.accounts.is_default")
            account.canSpend -> Component.translatable("gui.lnbeconomy.accounts.make_default")
            else -> Component.translatable("gui.lnbeconomy.accounts.viewer")
        }
        return Component.empty().append(role).append("\n").append(action)
    }
}
