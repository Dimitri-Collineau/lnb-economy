package fr.bde_teknantes.lnb.economy.command

import fr.bde_teknantes.lnb.economy.api.Handles
import fr.bde_teknantes.lnb.economy.api.TransferBody

/**
 * Parses what follows `/pay` and `/payfrom`. Brigadier's word arguments can't hold a `#`, so the commands take the
 * rest of the line and split it here: `<target> <amount> [message]`.
 */
object PayArguments {
    sealed interface Target {
        data class Player(val name: String) : Target
        data class Account(val handle: String) : Target
    }

    data class Parsed(val target: Target, val amount: Long, val memo: String?)

    sealed interface Result {
        data class Ok(val value: Parsed) : Result
        data class Error(val key: String) : Result
    }

    private val USERNAME = Regex("^[A-Za-z0-9_]{1,16}$")

    fun target(text: String): Target? = when {
        Handles.isHandle(text) -> Target.Account(Handles.bare(text))
        USERNAME.matches(text) -> Target.Player(text)
        else -> null
    }

    fun parse(input: String): Result {
        val parts = input.trim().split(Regex("\\s+"), limit = 3)
        if (parts.size < 2 || parts[0].isEmpty()) return Result.Error("economy.lnbeconomy.pay.usage")
        val target = target(parts[0]) ?: return Result.Error(
            if (parts[0].startsWith("#")) "economy.lnbeconomy.pay.bad_handle" else "economy.lnbeconomy.pay.bad_player",
        )
        val amount = parts[1].toLongOrNull()
        if (amount == null || amount < 1 || amount > TransferBody.MAX_AMOUNT) return Result.Error("economy.lnbeconomy.pay.bad_amount")
        val memo = parts.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
        if (memo != null && memo.length > TransferBody.MAX_MEMO_LENGTH) return Result.Error("economy.lnbeconomy.pay.memo_too_long")
        return Result.Ok(Parsed(target, amount, memo))
    }
}
