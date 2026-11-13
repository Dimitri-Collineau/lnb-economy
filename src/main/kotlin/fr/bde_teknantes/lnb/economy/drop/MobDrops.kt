package fr.bde_teknantes.lnb.economy.drop

import fr.bde_teknantes.lnb.economy.EconomyConfig
import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.account.PlayerAccounts
import fr.bde_teknantes.lnb.economy.account.RealPlayers
import fr.bde_teknantes.lnb.economy.api.MintBody
import fr.bde_teknantes.lnb.economy.api.MintResult
import fr.bde_teknantes.lnb.economy.api.Problems
import fr.bde_teknantes.lnb.economy.ledger.JournalEntry
import fr.bde_teknantes.lnb.economy.ledger.Outcome
import fr.bde_teknantes.lnb.economy.ledger.TransferJournal
import fr.bde_teknantes.lnb.economy.money.Texts
import fr.bde_teknantes.lnb.economy.network.EconomyStatus
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.entity.monster.Enemy
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.UUID

/**
 * Hostile mobs sometimes leave a few LNBits to the player who kills them. The money is new: the site mints it on the
 * player's personal account (reason `mobDrop`), right away, so the balance moves as the mob falls. With
 * `mobDrops.flushSeconds` above 0, drops are added up per player and sent that often instead, so a mob farm doesn't
 * call the site once per mob. Mobs born from a spawner give nothing unless the config says so.
 */
object MobDrops {
    private const val PURPOSE = "mobDrop"
    private const val ROUTE = "/mints"
    private const val FROM_SPAWNER = "lnbeconomy_from_spawner"

    /** The site caps one mint; a larger total is sent in several. */
    private const val MAX_PER_CALL = 100_000L

    /** After the site says mob drops are off, nothing is rolled for this long. */
    private const val PAUSE_MS = 10 * 60_000L

    private val waiting = HashMap<UUID, Long>()
    private var ticks = 0
    private var pausedUntil = 0L

    fun register() {
        TransferJournal.register(PURPOSE, ::finish)
    }

    fun onSpawn(event: FinalizeSpawnEvent) {
        if (event.spawnType == MobSpawnType.SPAWNER || event.spawnType == MobSpawnType.TRIAL_SPAWNER) {
            event.entity.persistentData.putBoolean(FROM_SPAWNER, true)
        }
    }

    fun onDeath(event: LivingDeathEvent) {
        val mob = event.entity
        if (mob !is Enemy || !EconomyConfig.MOB_DROPS.get()) return
        val player = event.source.entity as? ServerPlayer ?: return
        if (!RealPlayers.isReal(player) || System.currentTimeMillis() < pausedUntil) return
        if (!EconomyConfig.MOB_DROPS_SPAWNERS.get() && mob.persistentData.getBoolean(FROM_SPAWNER)) return
        if (PlayerAccounts.get(player.uuid)?.status != EconomyStatus.READY) return

        val roll = MobDropRoll(EconomyConfig.MOB_DROPS_CHANCE.get(), EconomyConfig.MOB_DROPS_MIN.get(), EconomyConfig.MOB_DROPS_MAX.get())
        val amount = roll.amount(player.random.nextDouble(), player.random.nextDouble())
        if (amount <= 0) return
        if (EconomyConfig.MOB_DROPS_FLUSH_SECONDS.get() == 0) send(player.uuid, amount) else waiting.merge(player.uuid, amount, Long::plus)
        player.displayClientMessage(
            Component.translatable("economy.lnbeconomy.mob_drop", Texts.amount(amount, player)).withStyle(ChatFormatting.GOLD), true,
        )
    }

    fun onServerTick(event: ServerTickEvent.Post) {
        if (waiting.isEmpty() || ++ticks < EconomyConfig.MOB_DROPS_FLUSH_SECONDS.get() * 20) return
        ticks = 0
        flush()
    }

    fun onLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        waiting.remove(event.entity.uuid)?.let { send(event.entity.uuid, it) }
    }

    /** Runs before the journal closes: what is waiting is written down and sent, now or at the next start. */
    fun onServerStopping(event: ServerStoppingEvent) = flush()

    private fun flush() {
        val batch = waiting.toMap()
        waiting.clear()
        batch.forEach(::send)
    }

    private fun send(player: UUID, total: Long) {
        var left = total
        while (left > 0) {
            val amount = minOf(left, MAX_PER_CALL)
            left -= amount
            TransferJournal.submit(ROUTE, PURPOSE, mapOf("player" to player.toString())) { key ->
                MintBody(key, player.toString(), amount, MintBody.MOB_DROP)
            }
        }
    }

    private fun finish(server: MinecraftServer, entry: JournalEntry, outcome: Outcome) {
        when (outcome) {
            is Outcome.Delayed -> Unit
            is Outcome.Done -> {
                val result = outcome.decode<MintResult>()
                PlayerAccounts.applyBalance(server, result.to.accountId, result.to.balance)
            }
            is Outcome.Refused -> {
                val problem = (outcome.result as? ApiResult.Http)?.problem
                if (problem == Problems.DISABLED) pausedUntil = System.currentTimeMillis() + PAUSE_MS
                LnbEconomy.LOGGER.warn("The site refused {} LNBits of mob drops for {}: {}", entry.amount, entry.data["player"], outcome.result.describe())
            }
        }
    }
}
