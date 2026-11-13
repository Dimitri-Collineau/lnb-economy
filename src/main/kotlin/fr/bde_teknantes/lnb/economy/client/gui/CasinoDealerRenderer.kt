package fr.bde_teknantes.lnb.economy.client.gui

import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.casino.CasinoDealer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.GeoEntityRenderer

/**
 * The licence dealer: our own model, texture and animations (see `visual-previews/scripts/dealer_geo.py`), drawn a
 * little under a player's size like a villager. His head hangs and never follows players: only his animations move it.
 */
class CasinoDealerRenderer(context: EntityRendererProvider.Context) : GeoEntityRenderer<CasinoDealer>(context, Model()) {
    init {
        withScale(SCALE)
        shadowRadius = 0.5f
    }

    private companion object {
        const val SCALE = 0.9375f
        val GEO: ResourceLocation = LnbEconomy.id("geo/entity/casino_dealer.geo.json")
        val TEXTURE: ResourceLocation = LnbEconomy.id("textures/entity/casino_dealer.png")
        val ANIMATIONS: ResourceLocation = LnbEconomy.id("animations/entity/casino_dealer.animation.json")
    }

    private class Model : GeoModel<CasinoDealer>() {
        override fun getModelResource(animatable: CasinoDealer): ResourceLocation = GEO
        override fun getTextureResource(animatable: CasinoDealer): ResourceLocation = TEXTURE
        override fun getAnimationResource(animatable: CasinoDealer): ResourceLocation = ANIMATIONS
    }
}
