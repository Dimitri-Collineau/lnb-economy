package fr.bde_teknantes.lnb.economy.mixin.casino;

import fr.bde_teknantes.lnb.economy.casino.minevegas.CasinoHooks;
import fr.minevegas.casino.CasinoExchange;
import fr.minevegas.casino.CasinoSettings;
import fr.minevegas.casino.CroupierSettings;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every bet MineVegas accepts goes through these: only chips are bets, whatever the tags, the global and per-machine
 * lists or the automatic ingot detection say, and a machine only takes its own casino's chips.
 * Exchange rules are turned off, so a payout is always in the stake's chips.
 */
@Mixin(CasinoSettings.class)
public abstract class CasinoSettingsMixin {
    @Inject(method = "accepts(Lfr/minevegas/casino/CroupierSettings;Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private static void lnbeconomy$onlyChips(CroupierSettings settings, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(CasinoHooks.isBettable(stack));
    }

    @Inject(method = "accepts(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private static void lnbeconomy$onlyOwnChips(BlockEntity machine, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(CasinoHooks.accepts(machine, stack));
    }

    @Inject(method = "exchange(Lfr/minevegas/casino/CroupierSettings;Lfr/minevegas/casino/CroupierSettings;Lnet/minecraft/world/item/ItemStack;)Lfr/minevegas/casino/CasinoExchange$Rule;",
            at = @At("HEAD"), cancellable = true)
    private static void lnbeconomy$noExchange(CroupierSettings own, CroupierSettings inherited, ItemStack stake, CallbackInfoReturnable<CasinoExchange.Rule> cir) {
        cir.setReturnValue(null);
    }
}
