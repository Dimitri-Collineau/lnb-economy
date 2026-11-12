package fr.bde_teknantes.lnb.economy.account

import java.text.Normalizer

/** The handle proposed for a new shared account from the name being typed: `Casino Royal !` gives `casino-royal`. */
object HandleSuggestion {
    private const val MAX = 24
    private val MARKS = Regex("\\p{M}+")
    private val OTHER = Regex("[^a-z0-9]+")

    fun of(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD).replace(MARKS, "").lowercase()
            .replace(OTHER, "-").trim('-').take(MAX).trimEnd('-')
}
