package fr.bde_teknantes.lnb.economy.mixin.casino;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.bde_teknantes.lnb.economy.casino.minevegas.CasinoHooks;
import fr.bde_teknantes.lnb.economy.casino.minevegas.MaxPayouts;
import fr.minevegas.casino.CasinoExchange;
import fr.minevegas.casino.SlotMachineBlockEntity;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slot machine: check the casino before the game, then count each destroyed stake and each payout in its chips in
 * circulation. Target: SlotMachineBlockEntity.start(ServerPlayer, int[]). The stake, the check and the payout happen in
 * the same tick.
 */
@Mixin(SlotMachineBlockEntity.class)
public abstract class SlotMachineBlockEntityMixin {
    private static final String START = "start(Lnet/minecraft/server/level/ServerPlayer;[I)V";

    @Shadow
    @Final
    private SimpleContainer bets;

    private BlockEntity lnbeconomy$self() {
        return (BlockEntity) (Object) this;
    }

    @Inject(method = START, at = @At(value = "INVOKE", target = MixinTargets.RETURN_REJECTED, shift = At.Shift.AFTER), cancellable = true)
    private void lnbeconomy$checkCasino(ServerPlayer initiator, int[] forced, CallbackInfo ci) {
        if (!CasinoHooks.allowPerStake(lnbeconomy$self(), this.bets, initiator, MaxPayouts.INSTANCE.getSlots(), 1)) {
            ci.cancel();
        }
    }

    @WrapOperation(method = START, at = @At(value = "INVOKE", target = MixinTargets.REMOVE_ITEM_NO_UPDATE))
    private ItemStack lnbeconomy$stakeDestroyed(SimpleContainer container, int slot, Operation<ItemStack> original) {
        ItemStack stake = original.call(container, slot);
        CasinoHooks.destroyed(lnbeconomy$self(), stake);
        return stake;
    }

    @WrapOperation(method = START, at = @At(value = "INVOKE", target = MixinTargets.PAYOUT))
    private List<ItemStack> lnbeconomy$payoutMade(ItemStack stake, int numerator, int denominator, int roll,
                                                  CasinoExchange.Rule rule, boolean refund,
                                                  Operation<List<ItemStack>> original) {
        return CasinoHooks.created(lnbeconomy$self(), original.call(stake, numerator, denominator, roll, null, refund));
    }
}
