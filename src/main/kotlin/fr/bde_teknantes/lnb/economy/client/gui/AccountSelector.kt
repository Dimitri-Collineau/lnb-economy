package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.network.AccountEntry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component

/**
 * An account picker in the casino look: the account's icon, a label, and its balance with the LNBit coin. A click moves
 * to the next of [choices]; the arrow shows only when there is more than one.
 */
open class AccountSelector(
    x: Int, y: Int, width: Int, height: Int,
    private val choices: List<AccountEntry>,
    initial: AccountEntry,
    private val label: (AccountEntry) -> Component,
    private val showBalance: Boolean = true,
) : AbstractButton(x, y, width, height, Component.empty()) {

    var value: AccountEntry = initial
        private set

    init {
        message = label(value)
    }

    override fun onPress() {
        if (choices.size < 2) return
        value = choices[(choices.indexOf(value) + 1) % choices.size]
        message = label(value)
    }

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val minecraft = Minecraft.getInstance()
        val font = minecraft.font
        val cycles = choices.size > 1
        val textY = y + (height - 8) / 2
        graphics.blitSprite(CasinoUi.SECONDARY.get(active, cycles && isHoveredOrFocused), x, y, width, height)
        graphics.blitSprite(if (value.isPersonal) CasinoUi.ACCOUNT_PERSONAL else CasinoUi.ACCOUNT_SHARED, x + 5, textY, 9, 9)
        val right = x + width - if (cycles) 14 else 6
        val balance = if (showBalance) Lnbit.compact(value.balance, minecraft.options.languageCode) else null
        // The name gives way to the balance: a long handle is cut, never drawn over it.
        val room = (balance?.let { right - font.width(it) - 4 } ?: right) - (x + 18)
        graphics.drawString(font, CasinoUi.fit(font, message.string, room), x + 18, textY, CasinoUi.IVORY, true)
        if (balance != null) graphics.drawString(font, balance, right - font.width(balance), textY, CasinoUi.GOLD_TEXT, true)
        if (cycles) graphics.blitSprite(CasinoUi.PAGE_FORWARD, x + width - 9, y + (height - 11) / 2, 7, 11)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)

    companion object {
        /** "Personal" or the shared account's handle. */
        fun name(account: AccountEntry): Component =
            if (account.isPersonal) Component.translatable("gui.lnbeconomy.accounts.personal") else Component.literal("#${account.handle}")
    }
}
