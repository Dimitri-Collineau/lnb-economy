package fr.bde_teknantes.lnb.economy.chip

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent

/**
 * Chips dropped on the ground and destroyed there (lava, fire, explosions, despawning, the void) are no longer owed
 * by their casino. Chips lost any other way (a chest blown up, /clear) stay counted until an operator fixes the count.
 */
object LostChips {
    /** Below this, an item falling out of the world is gone for good. */
    private const val VOID_DEPTH = 64

    fun destroyed(entity: ItemEntity) {
        val level = entity.level() as? ServerLevel ?: return
        val stack = entity.item
        val casino = Chips.casinoOf(stack) ?: return
        ChipLedger.get(level.server).add(casino, -Chips.valueOf(stack))
    }

    /** Runs last, so another mod giving the item more time wins. */
    fun onExpire(event: ItemExpireEvent) {
        if (event.extraLife <= 0) destroyed(event.entity)
    }

    fun onLeaveLevel(event: EntityLeaveLevelEvent) {
        val entity = event.entity as? ItemEntity ?: return
        if (entity.removalReason != Entity.RemovalReason.DISCARDED) return
        if (entity.y < event.level.minBuildHeight - VOID_DEPTH) destroyed(entity)
    }
}
