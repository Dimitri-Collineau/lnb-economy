package fr.bde_teknantes.lnb.economy.client

import com.mojang.blaze3d.platform.InputConstants
import fr.bde_teknantes.lnb.economy.network.TradeRequestPayload
import fr.bde_teknantes.lnb.economy.network.TradeStatePayload
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

/** The trade key, and the open trade's money and readiness as the server last sent them. */
object ClientTrade {
    /** Unbound by default, so it never takes another mod's key: each player picks one in the controls. */
    val KEY = KeyMapping("key.lnbeconomy.trade", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.lnbeconomy")

    /** How far the player the key points at may be, in blocks. */
    private const val REACH = 5.0

    @Volatile
    var state: TradeStatePayload = TradeStatePayload(0, null, 0, false, false, -1)
        private set

    fun update(payload: TradeStatePayload) {
        state = payload
    }

    fun registerKeys(event: RegisterKeyMappingsEvent) {
        event.register(KEY)
    }

    fun onClientTick(event: ClientTickEvent.Post) {
        while (KEY.consumeClick()) {
            val minecraft = Minecraft.getInstance()
            val self = minecraft.player ?: return
            val target = (minecraft.hitResult as? EntityHitResult)?.entity as? Player ?: continue
            if (self.distanceTo(target) <= REACH) PacketDistributor.sendToServer(TradeRequestPayload(target.id))
        }
    }
}
