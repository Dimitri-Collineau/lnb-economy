package fr.bde_teknantes.lnb.economy.mixin.casino;

import fr.bde_teknantes.lnb.economy.casino.minevegas.CasinoHooks;
import fr.minevegas.casino.CasinoBets;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The fallback used by menus without a machine: only chips. */
@Mixin(CasinoBets.class)
public abstract class CasinoBetsMixin {
    @Inject(method = "accepts(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private static void lnbeconomy$onlyChips(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(CasinoHooks.isBettable(stack));
    }
}
