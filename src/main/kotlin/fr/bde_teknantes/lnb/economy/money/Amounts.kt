package fr.bde_teknantes.lnb.economy.money

/**
 * LNBit amounts as players read them. Inside the mod and on the wire an amount is a [Long] of **cents** (1 LNBit =
 * 100), never a floating point number. Players read and type LNBits with up to two decimals.
 */
object Amounts {
    /** The compact sign for LNBits. The client's `lnbeconomy:icons` font draws it as a coin (see `Lnbit`). */
    const val SYMBOL = "Ⓛ"

    /** Cents in one LNBit. */
    const val CENTS = 100L

    /** Whole LNBits (what chips and the casino count in) as cents. */
    fun fromLnbits(whole: Long): Long = Math.multiplyExact(whole, CENTS)

    /** Cents as whole LNBits, rounding toward zero. */
    fun toWholeLnbits(cents: Long): Long = cents / CENTS

    /**
     * `1,250.5` in English, `1 250,5` (no-break space) in French. The `.00` of a whole amount is dropped.
     * [language] is Minecraft's code, like `fr_fr`.
     */
    fun format(cents: Long, language: String): String {
        val absolute = if (cents == Long.MIN_VALUE) error("Amount out of range") else Math.abs(cents)
        val whole = absolute / CENTS
        val fraction = (absolute % CENTS).toInt()
        val separator = groupSeparator(language)
        val digits = whole.toString()
        val grouped = buildString {
            digits.forEachIndexed { i, c ->
                if (i > 0 && (digits.length - i) % 3 == 0) append(separator)
                append(c)
            }
            if (fraction != 0) {
                append(decimalSeparator(language))
                append((fraction / 10).toString())
                append((fraction % 10).toString())
            }
        }
        return if (cents < 0) "-$grouped" else grouped
    }

    /** What a player typed, as cents: `.` or `,` as decimal mark, at most two decimals. Null when it isn't one. */
    fun parse(text: String): Long? {
        val match = PARSE.matchEntire(text.trim()) ?: return null
        val whole = match.groupValues[1].toLongOrNull() ?: return null
        val decimals = match.groupValues[2].padEnd(2, '0').toLong()
        if (whole > MAX_WHOLE) return null
        return whole * CENTS + decimals
    }

    /** [cents] as a player edits it: `1.5`, `12`, with a `.` and no grouping. */
    fun plain(cents: Long): String {
        val whole = cents / CENTS
        val fraction = (cents % CENTS).toInt()
        return when {
            fraction == 0 -> whole.toString()
            fraction % 10 == 0 -> "$whole.${fraction / 10}"
            else -> "$whole.${fraction.toString().padStart(2, '0')}"
        }
    }

    /** Whether [text] can become an amount by typing more: a text field's filter. */
    fun isTypable(text: String): Boolean = TYPABLE.matches(text)

    private const val MAX_WHOLE = 1_000_000_000_000L
    private val PARSE = Regex("^(\\d{1,13})(?:[.,](\\d{0,2}))?$")
    private val TYPABLE = Regex("^\\d*(?:[.,]\\d{0,2})?$")

    private fun groupSeparator(language: String): String = if (usesComma(language)) " " else ","

    private fun decimalSeparator(language: String): String = if (usesComma(language)) "," else "."

    private fun usesComma(language: String): Boolean = when (language.substringBefore('_').lowercase()) {
        "fr", "de", "es", "it", "pt", "ru", "pl", "cs", "sv", "nb", "fi", "uk" -> true
        else -> false
    }
}
