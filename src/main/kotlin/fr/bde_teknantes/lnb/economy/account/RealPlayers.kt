package fr.bde_teknantes.lnb.economy.account

import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.common.util.FakePlayer

/**
 * Money only moves for players at the keyboard. Simulated players (Create's Deployers and the like, all built on
 * NeoForge's [FakePlayer]) act with their placer's identity and could buy from a shop in a loop: shops, trades and
 * the auction house ignore them.
 */
object RealPlayers {
    fun isReal(player: Player): Boolean = player !is FakePlayer
}
