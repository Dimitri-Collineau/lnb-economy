package fr.bde_teknantes.lnb.economy.client.gui

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import fr.bde_teknantes.lnb.economy.shop.ShopBlockEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.phys.AABB

/**
 * Shows the item a shop sells under its glass cloche, turning slowly and bobbing a little. The cloche itself is part of
 * the block model, drawn after this in the translucent layer, so the glass covers the item.
 */
class ShopShowcaseRenderer(@Suppress("UNUSED_PARAMETER") context: BlockEntityRendererProvider.Context) : BlockEntityRenderer<ShopBlockEntity> {
    private companion object {
        /** The middle of the cloche, in blocks above the pedestal's bottom. */
        const val CENTER_Y = 20.5 / 16
        const val SCALE = 0.5f
        const val TURN_DEGREES_PER_TICK = 1.5f
        const val BOB = 0.03f
    }

    override fun render(shop: ShopBlockEntity, partialTick: Float, poseStack: PoseStack, buffer: MultiBufferSource, light: Int, overlay: Int) {
        val item = shop.template
        val level = shop.level ?: return
        if (item.isEmpty) return
        val time = level.gameTime + partialTick
        poseStack.pushPose()
        poseStack.translate(0.5, CENTER_Y + Mth.sin(time / 20f) * BOB, 0.5)
        poseStack.mulPose(Axis.YP.rotationDegrees(time * TURN_DEGREES_PER_TICK))
        poseStack.scale(SCALE, SCALE, SCALE)
        // Lit by the cloche's block, where the item really is.
        val showcaseLight = LevelRenderer.getLightColor(level, shop.blockPos.above())
        Minecraft.getInstance().itemRenderer.renderStatic(
            item, ItemDisplayContext.FIXED, showcaseLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, level, shop.blockPos.asLong().toInt(),
        )
        poseStack.popPose()
    }

    override fun getRenderBoundingBox(blockEntity: ShopBlockEntity): AABB = AABB(blockEntity.blockPos).expandTowards(0.0, 1.0, 0.0)
}
