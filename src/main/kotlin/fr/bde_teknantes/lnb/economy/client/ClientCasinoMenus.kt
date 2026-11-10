package fr.bde_teknantes.lnb.economy.client

import net.minecraft.client.Minecraft

/**
 * The casino of each open casino menu, as the server said, so the bet slots only take that casino's chips on the
 * client too. Read by the casino mixins on the client.
 */
object ClientCasinoMenus {
    private val casinos = HashMap<Int, String?>()

    fun set(containerId: Int, handle: String?) {
        // Only the open menu matters; drop the others.
        casinos.keys.retainAll { it == Minecraft.getInstance().player?.containerMenu?.containerId }
        casinos[containerId] = handle
    }

    /** Null when the menu has no casino or the server hasn't said yet. */
    @JvmStatic
    fun casinoOf(containerId: Int): String? = casinos[containerId]
}
