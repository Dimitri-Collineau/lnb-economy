package fr.bde_teknantes.lnb.economy.mixin.casino;

import fr.bde_teknantes.lnb.economy.casino.minevegas.CasinoHooks;
import fr.minevegas.casino.BlackjackMenu;
import fr.minevegas.casino.DuckDerbyMenu;
import fr.minevegas.casino.FortuneWheelMenu;
import fr.minevegas.casino.PlinkoMenu;
import fr.minevegas.casino.PokerMenu;
import fr.minevegas.casino.RouletteMenu;
import fr.minevegas.casino.SlotMachineMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The bet slots of every casino menu, on both sides: only chips of the machine's casino. On the client the casino
 * comes from the server, so the client never lets in a chip the server would refuse.
 */
@Mixin({SlotMachineMenu.class, RouletteMenu.class, FortuneWheelMenu.class, PlinkoMenu.class, DuckDerbyMenu.class, BlackjackMenu.class, PokerMenu.class})
public abstract class BetMenusMixin {
    @Inject(method = "acceptsBet(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void lnbeconomy$ownChipsOnly(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(CasinoHooks.acceptsInMenu((AbstractContainerMenu) (Object) this, stack));
    }
}
