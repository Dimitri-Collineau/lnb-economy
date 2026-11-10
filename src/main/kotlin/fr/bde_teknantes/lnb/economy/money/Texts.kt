package fr.bde_teknantes.lnb.economy.money

import fr.bde_teknantes.lnb.economy.api.Handles
import fr.bde_teknantes.lnb.economy.api.Problems
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/** Messages the server sends to players, formatted for each player's language. */
object Texts {
    fun language(player: ServerPlayer?): String = player?.clientInformation()?.language() ?: "en_us"

    /** `1,250 LNBits`. */
    fun amount(amount: Long, player: ServerPlayer?): Component = amount(amount, language(player))

    fun amount(amount: Long, language: String): Component = Component.translatable(
        if (amount == 1L) "economy.lnbeconomy.amount.one" else "economy.lnbeconomy.amount.many",
        Amounts.format(amount, language),
    )

    /** `#casino-royal`, or the personal account's name. */
    fun account(handle: String?): Component =
        if (handle == null) Component.translatable("economy.lnbeconomy.account.personal") else Component.literal(Handles.display(handle))

    /** The message for a failed call to the site. */
    fun problem(result: ApiResult<*>): Component = Component.translatable(Problems.messageKey(result), Problems.code(result))
}
