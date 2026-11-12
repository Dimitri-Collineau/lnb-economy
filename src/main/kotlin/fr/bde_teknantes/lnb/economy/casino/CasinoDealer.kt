package fr.bde_teknantes.lnb.economy.casino

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
 * The dealer who sells casino licences: a croupier who has seen better nights, placed at spawn by an operator with
 * `/economy npc licence [name]`. Like the auctioneer, it never moves, never looks around, never despawns, can't be
 * pushed or hurt, and only an operator in creative mode removes it, by hitting it.
 */
class CasinoDealer(type: EntityType<out CasinoDealer>, level: Level) : PathfinderMob(type, level) {
    init {
        setNoAi(true)
        setPersistenceRequired()
        isInvulnerable = true
        isCustomNameVisible = true
        if (customName == null) customName = Component.translatable("entity.lnbeconomy.casino_dealer")
    }

    override fun mobInteract(player: Player, hand: InteractionHand): InteractionResult {
        if (hand == InteractionHand.MAIN_HAND && player is ServerPlayer) Licenses.open(player, this)
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
