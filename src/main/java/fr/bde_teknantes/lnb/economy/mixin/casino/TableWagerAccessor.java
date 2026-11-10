package fr.bde_teknantes.lnb.economy.mixin.casino;

import fr.minevegas.casino.BlackjackTableBlockEntity;
import fr.minevegas.casino.PokerTableBlockEntity;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to the private wager list of the blackjack and poker tables. */
@Mixin({BlackjackTableBlockEntity.class, PokerTableBlockEntity.class})
public interface TableWagerAccessor {
    @Accessor("wager")
    List<BlackjackTableBlockEntity.Stake> lnbeconomy$wager();
}
