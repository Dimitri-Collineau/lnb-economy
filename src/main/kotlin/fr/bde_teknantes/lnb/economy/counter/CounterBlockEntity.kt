package fr.bde_teknantes.lnb.economy.counter

import fr.bde_teknantes.lnb.economy.ModContent
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import software.bernie.geckolib.animatable.GeoBlockEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil

/**
 * The counter's master block: animates the teller standing behind the wall. It idles, greets a player who opens the
 * counter, and counts chips after each operation. [greet] and [count] are called on the server; GeckoLib sends the
 * animation to every player who sees the counter.
 */
class CounterBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModContent.COUNTER_ENTITY.get(), pos, state), GeoBlockEntity {
    companion object {
        private const val CONTROLLER = "teller"
        private const val GREET = "greet"
        private const val COUNT = "count"
        private val IDLE_ANIM = RawAnimation.begin().thenLoop("animation.counter_teller.idle")
        private val GREET_ANIM = RawAnimation.begin().thenPlay("animation.counter_teller.greet")
        private val COUNT_ANIM = RawAnimation.begin().thenPlay("animation.counter_teller.count")
    }

    private val cache = GeckoLibUtil.createInstanceCache(this)

    fun greet() = triggerAnim(CONTROLLER, GREET)

    fun count() = triggerAnim(CONTROLLER, COUNT)

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController(this, CONTROLLER, 5) { state -> state.setAndContinue(IDLE_ANIM) }
                .triggerableAnim(GREET, GREET_ANIM)
                .triggerableAnim(COUNT, COUNT_ANIM),
        )
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache
}
