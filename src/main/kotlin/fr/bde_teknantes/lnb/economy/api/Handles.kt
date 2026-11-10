package fr.bde_teknantes.lnb.economy.api

/** Shared account handles: 3 to 24 lower-case letters, digits or dashes, always shown with a `#`. */
object Handles {
    private val PATTERN = Regex("^[a-z0-9-]{3,24}$")

    /** The handle without its `#`. */
    fun bare(handle: String): String = handle.trim().removePrefix("#")

    fun display(handle: String): String = "#" + bare(handle)

    /** Whether [text] (with its `#`) can be a handle. The site has the final say. */
    fun isHandle(text: String): Boolean = text.startsWith("#") && PATTERN.matches(bare(text))
}
