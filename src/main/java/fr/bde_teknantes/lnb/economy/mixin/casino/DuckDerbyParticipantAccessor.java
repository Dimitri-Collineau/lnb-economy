package fr.bde_teknantes.lnb.economy.mixin.casino;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to the private record DuckDerbyBlockEntity.Participant. */
@Mixin(targets = "fr.minevegas.casino.DuckDerbyBlockEntity$Participant")
public interface DuckDerbyParticipantAccessor {
    @Accessor("lane")
    int lnbeconomy$lane();

    @Accessor("stakes")
    List<ItemStack> lnbeconomy$stakes();
}
