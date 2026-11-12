package fr.bde_teknantes.lnb.economy.client.gui

import com.mojang.authlib.GameProfile
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.core.UUIDUtil
import net.minecraft.world.level.block.entity.SkullBlockEntity
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Players' faces, by username. A player who is on comes with their skin; anyone else is looked up once, like a
 * player head block does, and shows a default skin until (or unless) the answer comes.
 */
object Heads {
    private val profiles = ConcurrentHashMap<String, Optional<GameProfile>>()
    private val asked = ConcurrentHashMap.newKeySet<String>()

    fun draw(graphics: GuiGraphics, username: String, x: Int, y: Int, size: Int) {
        PlayerFaceRenderer.draw(graphics, skin(username), x, y, size)
    }

    private fun skin(username: String): PlayerSkin {
        val minecraft = Minecraft.getInstance()
        minecraft.connection?.onlinePlayers?.firstOrNull { it.profile.name.equals(username, ignoreCase = true) }?.let { return it.skin }
        val key = username.lowercase()
        if (asked.add(key)) {
            SkullBlockEntity.fetchGameProfile(username).thenAccept { profiles[key] = it }
        }
        val profile = profiles[key]?.orElse(null) ?: return DefaultPlayerSkin.get(UUIDUtil.createOfflinePlayerUUID(username))
        return minecraft.skinManager.getInsecureSkin(profile)
    }
}
