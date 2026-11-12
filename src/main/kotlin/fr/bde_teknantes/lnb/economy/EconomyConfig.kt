package fr.bde_teknantes.lnb.economy

import net.neoforged.neoforge.common.ModConfigSpec

/**
 * Server settings, in `config/lnbeconomy-server.toml`. Registered as COMMON so it is never synced to players.
 * The site's URL and the server's token come from LNB Warden's `lnbwarden-server.toml`.
 */
object EconomyConfig {
    /** MineVegas machine types, as `CasinoMachine.type` names them. */
    val GAMES = listOf("slots", "roulette", "wheel", "plinko", "derby", "blackjack", "poker")

    private val BUILDER = ModConfigSpec.Builder()

    val PAY_CONFIRM_ABOVE: ModConfigSpec.LongValue = BUILDER
        .comment("Payments above this amount ask the player to confirm with a click. 0 never asks.")
        .push("pay")
        .defineInRange("confirmAbove", 1000L, 0L, Long.MAX_VALUE)

    val REFRESH_MINUTES: ModConfigSpec.IntValue = BUILDER
        .pop()
        .comment("How often balances shown in game and casino balances are read again from the site, in minutes.")
        .push("cache")
        .defineInRange("refreshMinutes", 5, 1, 60)

    val CASINO_STALE_MINUTES: ModConfigSpec.IntValue = BUILDER
        .comment(
            "How long casino machines keep playing on the last known balance of their account when the site",
            "can't be reached, in minutes. After that they stop until the site answers again.",
        )
        .defineInRange("casinoStaleMinutes", 10, 1, 1440)

    private val GAME_SWITCHES: Map<String, ModConfigSpec.BooleanValue>

    /** Player shops: buying, selling back, and setting up new ones. */
    val SHOPS: ModConfigSpec.BooleanValue

    /** Trades between players. */
    val TRADES: ModConfigSpec.BooleanValue

    /** The auction house, from every place it opens. */
    val AUCTIONS: ModConfigSpec.BooleanValue

    /** Whether `/hdv` (`/ah`) opens the auction house, or players must go to an auction house block or NPC. */
    val AUCTIONS_COMMAND: ModConfigSpec.BooleanValue

    /** Whether hostile mobs leave LNBits to the player who kills them. */
    val MOB_DROPS: ModConfigSpec.BooleanValue
    val MOB_DROPS_CHANCE: ModConfigSpec.DoubleValue
    val MOB_DROPS_MIN: ModConfigSpec.IntValue
    val MOB_DROPS_MAX: ModConfigSpec.IntValue
    val MOB_DROPS_SPAWNERS: ModConfigSpec.BooleanValue
    val MOB_DROPS_FLUSH_SECONDS: ModConfigSpec.IntValue

    val SPEC: ModConfigSpec

    init {
        BUILDER.pop().comment("Casino games that can be played. A game turned off refuses every bet.").push("casino")
        GAME_SWITCHES = GAMES.associateWith { BUILDER.define(it, true) }
        BUILDER.pop().comment(
            "Each part of the economy can be turned off here, without touching the site. The site has its own",
            "switches (economy.shops, economy.trades, economy.auctions), and both must be on.",
        ).push("features")
        SHOPS = BUILDER.comment("Player shops.").define("shops", true)
        TRADES = BUILDER.comment("Trades between players.").define("trades", true)
        AUCTIONS = BUILDER.comment("The auction house.").define("auctions", true)
        AUCTIONS_COMMAND = BUILDER
            .comment("Whether /hdv (or /ah) opens the auction house anywhere. Off, players go to an auction house block or NPC.")
            .define("auctionsCommand", true)
        BUILDER.pop().comment(
            "LNBits left by hostile mobs to the player who kills them. The money is created by the site (economy.mobDrops",
            "must be on there too) on the player's personal account.",
        ).push("mobDrops")
        MOB_DROPS = BUILDER.comment("Whether hostile mobs give LNBits at all.").define("enabled", true)
        MOB_DROPS_CHANCE = BUILDER.comment("Chance that a killed hostile mob gives LNBits, from 0 (never) to 1 (always). 0.1 is 10 %.")
            .defineInRange("chance", 0.1, 0.0, 1.0)
        MOB_DROPS_MIN = BUILDER.comment("Fewest LNBits a drop gives.").defineInRange("min", 1, 1, 100_000)
        MOB_DROPS_MAX = BUILDER.comment("Most LNBits a drop gives.").defineInRange("max", 10, 1, 100_000)
        MOB_DROPS_SPAWNERS = BUILDER.comment("Whether mobs born from a spawner give LNBits too. Off, mob farms on spawners make no money.")
            .define("fromSpawners", false)
        MOB_DROPS_FLUSH_SECONDS = BUILDER.comment("How often each player's drops are added up and sent to the site, in seconds.")
            .defineInRange("flushSeconds", 30, 5, 600)
        SPEC = BUILDER.pop().build()
    }

    fun gameEnabled(game: String): Boolean = GAME_SWITCHES[game]?.get() ?: false
}
