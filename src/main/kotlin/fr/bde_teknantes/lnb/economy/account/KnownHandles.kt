package fr.bde_teknantes.lnb.economy.account

/**
 * Shared accounts the server knows about, for completion: no site route lists them, so it's the player's own
 * accounts plus those another part of the mod adds (casinos linked to a machine or a counter).
 */
object KnownHandles {
    @Volatile
    var extra: () -> Collection<String> = { emptyList() }
}
