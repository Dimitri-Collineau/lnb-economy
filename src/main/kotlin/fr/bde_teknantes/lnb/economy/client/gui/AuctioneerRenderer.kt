package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.auction.Auctioneer
import net.minecraft.client.model.VillagerModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.resources.ResourceLocation

/** The auctioneer, as a plain villager until the visual pass gives it its own look. */
class AuctioneerRenderer(context: EntityRendererProvider.Context) :
    MobRenderer<Auctioneer, VillagerModel<Auctioneer>>(context, VillagerModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.5f) {
    override fun getTextureLocation(entity: Auctioneer): ResourceLocation = TEXTURE

    private companion object {
        val TEXTURE: ResourceLocation = ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png")
    }
}
