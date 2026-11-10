package fr.bde_teknantes.lnb.economy.mixin.casino;

/** Descriptors of the MineVegas and Minecraft methods the casino mixins hook into (RECON.md, section 7). */
final class MixinTargets {
    static final String RETURN_REJECTED =
            "Lfr/minevegas/casino/CasinoSettings;returnRejected(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/SimpleContainer;Lnet/minecraft/server/level/ServerPlayer;)V";

    static final String REMOVE_ITEM_NO_UPDATE =
            "Lnet/minecraft/world/SimpleContainer;removeItemNoUpdate(I)Lnet/minecraft/world/item/ItemStack;";

    static final String PAYOUT =
            "Lfr/minevegas/casino/CasinoExchange;payout(Lnet/minecraft/world/item/ItemStack;IIILfr/minevegas/casino/CasinoExchange$Rule;Z)Ljava/util/List;";

    static final String DERBY_RETURN_REJECTED =
            "Lfr/minevegas/casino/DuckDerbyBlockEntity;returnRejected(Lnet/minecraft/server/level/ServerPlayer;Lfr/minevegas/casino/DuckDerbyMenu;)V";

    static final String DERBY_SETTLE =
            "Lfr/minevegas/casino/DerbyCredits;settle(Ljava/lang/String;Ljava/util/List;J)Z";

    private MixinTargets() {
    }
}
