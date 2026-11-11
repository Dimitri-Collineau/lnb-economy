package fr.bde_teknantes.lnb.economy.mixin.casino;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.bde_teknantes.lnb.economy.casino.minevegas.CasinoHooks;
import fr.minevegas.casino.BlackjackTableBlockEntity;
import fr.minevegas.casino.CasinoExchange;
import fr.minevegas.casino.BlackjackRound;
import fr.minevegas.casino.BlackjackTableBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blackjack result: the whole wager is destroyed and the round's reservation ends, then each payout is counted.
 * Target: the private inner class BlackjackTableBlockEntity$Events, method decided(Result, int).
 */
@Mixin(targets = "fr.minevegas.casino.BlackjackTableBlockEntity$Events")
public abstract class BlackjackEventsMixin {
    private static final String DECIDED = "decided(Lfr/minevegas/casino/BlackjackRound$Result;I)V";

    @Shadow
    @Final
    BlackjackTableBlockEntity this$0;

    @Inject(method = DECIDED, at = @At("HEAD"))
    private void lnbeconomy$wagerDestroyed(BlackjackRound.Result result, int at, CallbackInfo ci) {
        List<ItemStack> stakes = new ArrayList<>();
        for (BlackjackTableBlockEntity.Stake stake : ((TableWagerAccessor) (Object) this.this$0).lnbeconomy$wager()) {
            stakes.add(stake.display());
        }
        CasinoHooks.release(this.this$0);
        CasinoHooks.destroyedAll(this.this$0, stakes);
    }

    /** Called for losses too, with an empty payout. */
    @WrapOperation(method = DECIDED, at = @At(value = "INVOKE", target = MixinTargets.PAYOUT))
    private List<ItemStack> lnbeconomy$payoutMade(ItemStack stake, int numerator, int denominator, int roll,
                                                  CasinoExchange.Rule rule, boolean refund,
                                                  Operation<List<ItemStack>> original) {
        return CasinoHooks.created(this.this$0, CasinoHooks.payout(stake, numerator, denominator, roll, refund,
                () -> original.call(stake, numerator, denominator, roll, null, refund)));
    }
}
