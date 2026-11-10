package fr.bde_teknantes.lnb.economy.money

/** LNBit amounts as players read them: whole numbers, grouped by thousands the way their language does it. */
object Amounts {
    /** The compact sign for LNBits, used in small interfaces. */
    const val SYMBOL = "Ⓛ"

    /** `1,250` in English, `1 250` (no-break space) in French. [language] is Minecraft's code, like `fr_fr`. */
    fun format(amount: Long, language: String): String {
        val separator = separatorFor(language)
        val digits = Math.absExact(amount).toString()
        val grouped = buildString {
            digits.forEachIndexed { i, c ->
                if (i > 0 && (digits.length - i) % 3 == 0) append(separator)
                append(c)
            }
        }
        return if (amount < 0) "-$grouped" else grouped
    }

    /** `Ⓛ 1 250`, for the inventory and block screens. */
    fun compact(amount: Long, language: String): String = "$SYMBOL ${format(amount, language)}"

    private fun separatorFor(language: String): String = when (language.substringBefore('_').lowercase()) {
        "fr", "de", "es", "it", "pt", "ru", "pl", "cs", "sv", "nb", "fi", "uk" -> " "
        else -> ","
    }
}
