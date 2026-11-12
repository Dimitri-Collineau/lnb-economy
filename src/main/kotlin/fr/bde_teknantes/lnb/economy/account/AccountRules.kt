package fr.bde_teknantes.lnb.economy.account

import fr.bde_teknantes.lnb.economy.api.AccountTypes
import fr.bde_teknantes.lnb.economy.api.Handles

/**
 * What a shared account's handle, name and type must look like, checked before the site is asked. The site checks
 * again and has the final say. Each function returns the translation key of what is wrong, or null.
 */
object AccountRules {
    const val MAX_NAME = 32
    private val USERNAME = Regex("^[A-Za-z0-9_]{3,16}$")

    fun handleProblem(handle: String): String? =
        if (Handles.isHandle(Handles.display(handle))) null else "economy.lnbeconomy.pay.bad_handle"

    fun nameProblem(name: String): String? =
        if (name.isNotBlank() && name.trim().length <= MAX_NAME) null else "economy.lnbeconomy.account.bad_name"

    fun typeProblem(type: String): String? = if (type in AccountTypes.ALL) null else "economy.lnbeconomy.account.bad_type"

    fun isUsername(text: String): Boolean = USERNAME.matches(text)
}
