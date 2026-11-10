package fr.bde_teknantes.lnb.economy.casino

/**
 * On the client: the casino of the open casino menu, as the server said, so its bet slots only take that casino's
 * chips there too. Client menus don't know their machine.
 */
object MenuCasinos {
    private var containerId = -1
    private var handle: String? = null

    fun set(containerId: Int, handle: String?) {
        this.containerId = containerId
        this.handle = handle
    }

    /** Null when the menu has no casino or the server hasn't said yet. */
    fun casinoOf(containerId: Int): String? = if (containerId == this.containerId) handle else null
}
