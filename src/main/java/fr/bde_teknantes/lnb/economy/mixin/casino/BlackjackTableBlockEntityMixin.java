package fr.bde_teknantes.lnb.economy.mixin.casino;

import fr.bde_teknantes.lnb.economy.casino.minevegas.CasinoHooks;
import fr.bde_teknantes.lnb.economy.casino.minevegas.MaxPayouts;
import fr.minevegas.casino.BlackjackTableBlockEntity;
import fr.minevegas.casino.BlackjackMenu;
import fr.minevegas.casino.BlackjackTableBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blackjack: when a round begins, check the casino can cover the best result (the double down included) and hold that much
 * back until the result; check again when the player raises. Stakes and payouts are counted in {@link BlackjackEventsMixin}.
 */
@Mixin(BlackjackTableBlockEntity.class)
public abstract class BlackjackTableBlockEntityMixin {
    @Shadow
    @Final
    private List<BlackjackTableBlockEntity.Stake> wager;

    @Inject(method = "beginRound(Lnet/minecraft/server/level/ServerPlayer;Lfr/minevegas/casino/BlackjackMenu;)Z",
            at = @At(value = "INVOKE", target = MixinTargets.RETURN_REJECTED, shift = At.Shift.AFTER), cancellable = true)
    private void lnbeconomy$checkRound(ServerPlayer player, BlackjackMenu menu, CallbackInfoReturnable<Boolean> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        List<ItemStack> stakes = CasinoHooks.acceptedStakesOf(self, menu.bets());
        if (!CasinoHooks.allowTable(self, player, stakes, MaxPayouts.BLACKJACK_DOUBLE, MaxPayouts.INSTANCE.getBlackjackNaturalHalves(), MaxPayouts.INSTANCE.getBlackjackDoubledHalves(), 2)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "takeExtraStake(Lnet/minecraft/server/level/ServerPlayer;)Z", at = @At("HEAD"), cancellable = true)
    private void lnbeconomy$checkRaise(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        List<ItemStack> current = new ArrayList<>();
        for (BlackjackTableBlockEntity.Stake stake : this.wager) {
            current.add(stake.display());
        }
        if (!CasinoHooks.allowTable(self, player, current, MaxPayouts.BLACKJACK_DOUBLE, 0, MaxPayouts.INSTANCE.getBlackjackDoubledHalves(), 2)) {
            cir.setReturnValue(false);
        }
    }
}
