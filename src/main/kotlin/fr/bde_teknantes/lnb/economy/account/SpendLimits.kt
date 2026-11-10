package fr.bde_teknantes.lnb.economy.account

/**
 * Money on an account that the server keeps back, on top of the site's own rules.
 * A casino's account backs its chips in circulation, so only its free balance can be paid out with `/pay`.
 */
object SpendLimits {
    /** The most that may leave the shared account [handle] (no `#`) now, or null when the server keeps nothing back. */
    @Volatile
    var available: (handle: String) -> Long? = { null }
}
