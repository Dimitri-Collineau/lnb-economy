package fr.bde_teknantes.lnb.economy.client

import fr.bde_teknantes.lnb.economy.client.gui.PokerScreen
import fr.bde_teknantes.lnb.economy.network.PokerStatePayload
import net.minecraft.client.Minecraft

/** The poker table on screen, as the server last sent it. */
object ClientPoker {
    var state: PokerStatePayload? = null
        private set

    /** When [state] arrived, to count its timer down between two updates. */
    var receivedAt = 0L
        private set

    fun update(payload: PokerStatePayload) {
        val minecraft = Minecraft.getInstance()
        val current = minecraft.screen as? PokerScreen
        if (current != null && current.pos == payload.pos) {
            set(payload)
        } else if (payload.open) {
            set(payload)
            minecraft.setScreen(PokerScreen(payload.pos))
        }
    }

    private fun set(payload: PokerStatePayload) {
        state = payload
        receivedAt = System.currentTimeMillis()
    }

    /** Ticks left in the table's current phase, counted down since the last update. */
    fun ticksLeft(): Int {
        val state = state ?: return 0
        return maxOf(0, state.ticksLeft - ((System.currentTimeMillis() - receivedAt) / 50).toInt())
    }
}
