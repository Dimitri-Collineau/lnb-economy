package fr.bde_teknantes.lnb.economy.auction

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

/**
 * The auctioneer: an NPC that opens the auction house. It never moves, never despawns, can't be pushed or hurt, and
 * only an operator in creative mode removes it, by hitting it. Spawned with `/economy npc hdv [name]`. Its look is a
 * plain villager for now.
 */
class Auctioneer(type: EntityType<out Auctioneer>, level: Level) : PathfinderMob(type, level) {
    init {
        setNoAi(true)
        setPersistenceRequired()
        isInvulnerable = true
        isCustomNameVisible = true
        if (customName == null) customName = Component.translatable("entity.lnbeconomy.auctioneer")
    }

    override fun mobInteract(player: Player, hand: InteractionHand): InteractionResult {
        if (hand == InteractionHand.MAIN_HAND && player is ServerPlayer) Auctions.open(player)
        return InteractionResult.sidedSuccess(level().isClientSide)
    }

    override fun hurt(source: DamageSource, amount: Float): Boolean {
        val attacker = source.entity as? ServerPlayer
        if (attacker != null && attacker.isCreative && attacker.hasPermissions(2)) discard()
        return false
    }

    override fun isPushable(): Boolean = false

    override fun removeWhenFarAway(distanceToClosestPlayer: Double): Boolean = false

    override fun isInvulnerableTo(source: DamageSource): Boolean = true

    companion object {
        fun attributes(): AttributeSupplier.Builder = Mob.createMobAttributes()
    }
}
