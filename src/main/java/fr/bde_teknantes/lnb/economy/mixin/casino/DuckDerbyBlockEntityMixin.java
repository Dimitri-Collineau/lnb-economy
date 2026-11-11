package fr.bde_teknantes.lnb.economy.mixin.casino;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.bde_teknantes.lnb.economy.casino.minevegas.CasinoHooks;
import fr.bde_teknantes.lnb.economy.casino.minevegas.MaxPayouts;
import fr.minevegas.casino.CasinoExchange;
import fr.minevegas.casino.DerbyCredits;
import fr.minevegas.casino.DuckDerbyBlockEntity;
import fr.minevegas.casino.DuckDerbyMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kotlin.Pair;
import net.minecraft.server.level.ServerLevel;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Duck Derby.
 * Legacy path (bets stored in the machine): the same hooks as the other spinning games, in startLegacy.
 * Personal and group races (bets held in escrow by DerbyCredits): check and reserve in startSolo and joinGroup,
 * then count the escrow settlement in launchPersonal, which also ends the reservation.
 */
@Mixin(DuckDerbyBlockEntity.class)
public abstract class DuckDerbyBlockEntityMixin {
    private static final String START_LEGACY = "startLegacy(Lnet/minecraft/server/level/ServerPlayer;I)V";
    private static final String START_SOLO = "startSolo(Lnet/minecraft/server/level/ServerPlayer;Lfr/minevegas/casino/DuckDerbyMenu;I)Z";
    private static final String JOIN_GROUP = "joinGroup(Lnet/minecraft/server/level/ServerPlayer;Lfr/minevegas/casino/DuckDerbyMenu;)Z";
    private static final String LAUNCH_PERSONAL = "launchPersonal(Lnet/minecraft/server/level/ServerLevel;I)V";

    @Shadow
    @Final
    private SimpleContainer bets;

    @Shadow
    @Final
    private Map<UUID, Object> participants;

    private BlockEntity lnbeconomy$self() {
        return (BlockEntity) (Object) this;
    }

    // Legacy path

    @Inject(method = START_LEGACY, at = @At(value = "INVOKE", target = MixinTargets.RETURN_REJECTED, shift = At.Shift.AFTER), cancellable = true)
    private void lnbeconomy$checkLegacy(ServerPlayer initiator, int forced, CallbackInfo ci) {
        if (DuckDerbyMenu.stakedLane(this.bets) < 0) {
            // No bet, or bets on several ducks: MineVegas refuses the race by itself.
            return;
        }
        if (!CasinoHooks.allowPerStake(lnbeconomy$self(), this.bets, initiator, MaxPayouts.DERBY_WIN, 1)) {
            ci.cancel();
        }
    }

    @WrapOperation(method = START_LEGACY, at = @At(value = "INVOKE", target = MixinTargets.REMOVE_ITEM_NO_UPDATE))
    private ItemStack lnbeconomy$legacyStakeDestroyed(SimpleContainer container, int slot, Operation<ItemStack> original) {
        ItemStack stake = original.call(container, slot);
        CasinoHooks.destroyed(lnbeconomy$self(), stake);
        return stake;
    }

    @WrapOperation(method = START_LEGACY, at = @At(value = "INVOKE", target = MixinTargets.PAYOUT))
    private List<ItemStack> lnbeconomy$legacyPayoutMade(ItemStack stake, int numerator, int denominator, int roll,
                                                        CasinoExchange.Rule rule, boolean refund,
                                                        Operation<List<ItemStack>> original) {
        return CasinoHooks.created(lnbeconomy$self(), CasinoHooks.payout(stake, numerator, denominator, roll, refund,
                () -> original.call(stake, numerator, denominator, roll, null, refund)));
    }

    // Personal and group races

    @Inject(method = START_SOLO, at = @At(value = "INVOKE", target = MixinTargets.DERBY_RETURN_REJECTED, shift = At.Shift.AFTER), cancellable = true)
    private void lnbeconomy$checkSolo(ServerPlayer player, DuckDerbyMenu menu, int forced, CallbackInfoReturnable<Boolean> cir) {
        int lane = menu.stakedLane();
        if (lane < 0) {
            return;
        }
        List<ItemStack> stakes = CasinoHooks.stakesOf(menu.bets());
        if (!CasinoHooks.allowDerby(lnbeconomy$self(), player, List.of(new Pair<>(lane, stakes)), stakes)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = JOIN_GROUP, at = @At(value = "INVOKE", target = MixinTargets.DERBY_RETURN_REJECTED, shift = At.Shift.AFTER), cancellable = true)
    private void lnbeconomy$checkGroup(ServerPlayer player, DuckDerbyMenu menu, CallbackInfoReturnable<Boolean> cir) {
        int lane = menu.stakedLane();
        if (lane < 0) {
            return;
        }
        // Everyone already enrolled, plus the newcomer.
        List<Pair<Integer, List<ItemStack>>> runners = new ArrayList<>();
        for (Object value : this.participants.values()) {
            DuckDerbyParticipantAccessor p = (DuckDerbyParticipantAccessor) value;
            runners.add(new Pair<>(p.lnbeconomy$lane(), p.lnbeconomy$stakes()));
        }
        List<ItemStack> stakes = CasinoHooks.stakesOf(menu.bets());
        runners.add(new Pair<>(lane, stakes));
        if (!CasinoHooks.allowDerby(lnbeconomy$self(), player, runners, stakes)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = LAUNCH_PERSONAL, at = @At("HEAD"))
    private void lnbeconomy$raceStarts(ServerLevel level, int forced, CallbackInfo ci) {
        // Every participant is settled below, in this tick.
        CasinoHooks.release(lnbeconomy$self());
    }

    @WrapOperation(method = LAUNCH_PERSONAL, at = @At(value = "INVOKE", target = MixinTargets.DERBY_SETTLE))
    private boolean lnbeconomy$settle(DerbyCredits credits, String escrow, List<ItemStack> reward, long due, Operation<Boolean> original) {
        if (!credits.held(escrow)) {
            // settle() ignores escrows that are no longer held, so nothing moves.
            return original.call(credits, escrow, reward, due);
        }
        return original.call(credits, escrow, CasinoHooks.settleDerby(lnbeconomy$self(), credits.stakes(escrow), reward), due);
    }
}
