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

    val SPEC: ModConfigSpec

    init {
        BUILDER.pop().comment("Casino games that can be played. A game turned off refuses every bet.").push("casino")
        GAME_SWITCHES = GAMES.associateWith { BUILDER.define(it, true) }
        SPEC = BUILDER.pop().build()
    }

    fun gameEnabled(game: String): Boolean = GAME_SWITCHES[game]?.get() ?: false
}
