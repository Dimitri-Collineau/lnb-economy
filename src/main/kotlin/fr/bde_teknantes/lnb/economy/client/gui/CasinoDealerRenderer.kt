package fr.bde_teknantes.lnb.economy.client.gui

import com.mojang.blaze3d.vertex.PoseStack
import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.casino.CasinoDealer
import net.minecraft.client.model.VillagerModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth

/**
 * The licence dealer: a vanilla villager (its texture referenced, never copied) under our own worn-out croupier
 * clothes, drawn like a profession layer. His head hangs forward and to one side, and nods off slowly; it never
 * follows players.
 */
class CasinoDealerRenderer(context: EntityRendererProvider.Context) :
    MobRenderer<CasinoDealer, VillagerModel<CasinoDealer>>(context, Slumped(context.bakeLayer(ModelLayers.VILLAGER)), 0.5f) {

    init {
        addLayer(Clothes(this))
    }

    override fun getTextureLocation(entity: CasinoDealer): ResourceLocation = VILLAGER

    private companion object {
        val VILLAGER: ResourceLocation = ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png")
        val CLOTHES: ResourceLocation = LnbEconomy.id("textures/entity/casino_dealer.png")
    }

    private class Slumped(root: ModelPart) : VillagerModel<CasinoDealer>(root) {
        override fun setupAnim(entity: CasinoDealer, limbSwing: Float, limbSwingAmount: Float, ageInTicks: Float, netHeadYaw: Float, headPitch: Float) {
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, 0f, 0f)
            val nod = Mth.sin(ageInTicks * 0.04f)
            head.xRot = 0.32f + 0.06f * nod
            head.yRot = 0f
            head.zRot = 0.12f + 0.02f * nod
        }
    }

    private class Clothes(parent: RenderLayerParent<CasinoDealer, VillagerModel<CasinoDealer>>) :
        RenderLayer<CasinoDealer, VillagerModel<CasinoDealer>>(parent) {
        override fun render(
            poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int, entity: CasinoDealer, limbSwing: Float, limbSwingAmount: Float,
            partialTick: Float, ageInTicks: Float, netHeadYaw: Float, headPitch: Float,
        ) {
            if (entity.isInvisible) return
            parentModel.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityTranslucent(CLOTHES)), packedLight, OverlayTexture.NO_OVERLAY)
        }
    }
}
