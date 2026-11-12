package fr.bde_teknantes.lnb.economy.client.gui

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.counter.CounterBlock
import fr.bde_teknantes.lnb.economy.counter.CounterBlockEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.level.LightLayer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import software.bernie.geckolib.animation.AnimationState
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.GeoBlockRenderer
import software.bernie.geckolib.renderer.GeoRenderer
import software.bernie.geckolib.renderer.layer.GeoRenderLayer

/**
 * Draws the counter's teller behind the wall (the wall itself is a regular block model). The teller is a vanilla villager,
 * drawn with the vanilla villager texture (referenced, never copied) and our "teller" clothes on top, at 90 % size so
 * the head fits under the wall's cornice. The head turns toward the local player when they stand in front of the counter.
 */
class CounterTellerRenderer(@Suppress("UNUSED_PARAMETER") context: BlockEntityRendererProvider.Context) :
    GeoBlockRenderer<CounterBlockEntity>(TellerModel()) {

    init {
        withScale(SCALE)
        addRenderLayer(Clothes(this))
    }

    /**
     * Lights the teller like a villager standing there: the brightest of the blocks it fills (the master and the one
     * above) and the ones just behind it, rather than the master block alone, which the wall may shade.
     */
    override fun render(
        animatable: CounterBlockEntity, partialTick: Float, poseStack: PoseStack, bufferSource: MultiBufferSource,
        packedLight: Int, packedOverlay: Int,
    ) {
        val level = animatable.level ?: return super.render(animatable, partialTick, poseStack, bufferSource, packedLight, packedOverlay)
        val pos = animatable.blockPos
        val behind = pos.relative(animatable.blockState.getValue(CounterBlock.FACING).opposite)
        var block = 0
        var sky = 0
        for (p in listOf(pos, pos.above(), behind, behind.above())) {
            block = maxOf(block, level.getBrightness(LightLayer.BLOCK, p))
            sky = maxOf(sky, level.getBrightness(LightLayer.SKY, p))
        }
        super.render(animatable, partialTick, poseStack, bufferSource, LightTexture.pack(block, sky), packedOverlay)
    }

    /** The teller stands two blocks tall and the wall spans three: keep rendering while any of it shows. */
    override fun getRenderBoundingBox(blockEntity: CounterBlockEntity): AABB =
        AABB(blockEntity.blockPos).inflate(1.5, 0.0, 1.5).expandTowards(0.0, 1.5, 0.0)

    private companion object {
        const val SCALE = 0.9f
        val GEO: ResourceLocation = LnbEconomy.id("geo/block/counter_teller.geo.json")
        val ANIMATIONS: ResourceLocation = LnbEconomy.id("animations/block/counter_teller.animation.json")
        val VILLAGER: ResourceLocation = ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png")
        val CLOTHES: ResourceLocation = LnbEconomy.id("textures/block/counter_teller.png")

        /** How far behind the master block's center the teller's head is, and how high. */
        const val HEAD_BACK = 4.5 / 16
        const val HEAD_HEIGHT = 1.7
        const val TRACK_RANGE = 8.0
        const val MAX_YAW = 60f
    }

    private class TellerModel : GeoModel<CounterBlockEntity>() {
        override fun getModelResource(animatable: CounterBlockEntity): ResourceLocation = GEO
        override fun getTextureResource(animatable: CounterBlockEntity): ResourceLocation = VILLAGER
        override fun getAnimationResource(animatable: CounterBlockEntity): ResourceLocation = ANIMATIONS

        override fun setCustomAnimations(animatable: CounterBlockEntity, instanceId: Long, state: AnimationState<CounterBlockEntity>) {
            val head = animationProcessor.getBone("head") ?: return
            val player = Minecraft.getInstance().player ?: return
            val facing = animatable.blockState.getValue(CounterBlock.FACING)
            val back = facing.opposite.normal
            val eyes = Vec3.atBottomCenterOf(animatable.blockPos).add(back.x * HEAD_BACK, HEAD_HEIGHT, back.z * HEAD_BACK)
            val d = player.getEyePosition(state.partialTick).subtract(eyes)
            if (d.lengthSqr() > TRACK_RANGE * TRACK_RANGE) return
            // Only the yaw follows the player (same convention as a mob's head, relative to the body): the head never
            // tilts up or down, so crouching next to the counter changes nothing.
            val yaw = Mth.wrapDegrees((Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG).toFloat() - 90f - facing.toYRot())
            if (yaw !in -90f..90f) return // behind the teller: don't twist around
            head.rotY += yaw.coerceIn(-MAX_YAW, MAX_YAW) * Mth.DEG_TO_RAD
        }
    }

    /** The teller's clothes, drawn over the vanilla villager like a profession layer. The visor is translucent. */
    private class Clothes(renderer: GeoRenderer<CounterBlockEntity>) : GeoRenderLayer<CounterBlockEntity>(renderer) {
        override fun render(
            poseStack: PoseStack, animatable: CounterBlockEntity, bakedModel: BakedGeoModel, renderType: RenderType?,
            bufferSource: MultiBufferSource, buffer: VertexConsumer?, partialTick: Float, packedLight: Int, packedOverlay: Int,
        ) {
            val type = RenderType.entityTranslucent(CLOTHES)
            renderer.reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type), partialTick, packedLight, packedOverlay, -1)
        }
    }
}
