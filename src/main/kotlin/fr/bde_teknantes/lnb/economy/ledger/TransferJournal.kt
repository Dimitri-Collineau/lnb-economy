package fr.bde_teknantes.lnb.economy.ledger

import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.api.EconomyApi
import fr.bde_teknantes.lnb.economy.api.Party
import fr.bde_teknantes.lnb.economy.api.TransferBody
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.UUID

/**
 * Every money move goes through here, whatever its route (a transfer, a shop order, a trade...): written to
 * `<world>/data/lnbeconomy_journal.json` before it is sent, sent again with the same idempotency key until the site
 * confirms or refuses it (after a restart too), then finished by the [Handler] registered for its purpose.
 */
object TransferJournal {
    /** Finishes an entry on the server thread. Must not throw. */
    fun interface Handler {
        fun onOutcome(server: MinecraftServer, entry: JournalEntry, outcome: Outcome)
    }

    private const val FILE = "lnbeconomy_journal.json"
    private const val TICKS_BETWEEN_CHECKS = 20

    private val handlers = HashMap<String, Handler>()
    private var core: JournalCore? = null
    private var server: MinecraftServer? = null
    private var ticks = 0

    val pending: List<JournalEntry> get() = core?.pending.orEmpty()

    fun register(purpose: String, handler: Handler) {
        check(handlers.putIfAbsent(purpose, handler) == null) { "Journal purpose $purpose is already registered" }
    }

    /**
     * Records and sends a transfer. [from] and [to] are as in the site's `POST /transfers`.
     * Returns the idempotency key.
     */
    fun submit(
        from: Party,
        to: Party,
        amount: Long,
        kind: String,
        memo: String?,
        purpose: String,
        data: Map<String, String> = emptyMap(),
    ): String {
        val body = TransferBody(newKey(), from, to, amount, kind, memo?.take(TransferBody.MAX_MEMO_LENGTH))
        return submit(JournalEntry.of(JournalEntry.TRANSFERS, body, purpose, data))
    }

    /**
     * Records and sends a POST to [route] (under `/api/economy/server`). [body] gets a new idempotency key, which it
     * must put in its `idempotencyKey` field. Returns the key.
     */
    fun submit(route: String, purpose: String, data: Map<String, String> = emptyMap(), body: (key: String) -> Any): String =
        submit(JournalEntry.of(route, body(newKey()), purpose, data))

    private fun submit(entry: JournalEntry): String {
        val core = checkNotNull(core) { "The transfer journal only runs on a started server" }
        check(entry.purpose in handlers) { "No journal handler for ${entry.purpose}" }
        core.submit(entry)
        return entry.key
    }

    private fun newKey(): String = UUID.randomUUID().toString()

    fun retryNow(key: String): Boolean = core?.retryNow(key) ?: false

    fun onServerStarted(event: ServerStartedEvent) {
        val server = event.server
        this.server = server
        val file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE)
        val core = JournalCore(
            store = JournalStore(file),
            send = { entry -> EconomyApi.post(entry.route, entry.body) },
            clock = System::currentTimeMillis,
            executor = server::execute,
            onOutcome = ::finish,
        )
        core.load()
        this.core = core
        if (core.pending.isNotEmpty()) {
            LnbEconomy.LOGGER.info("{} money move(s) from a previous run are waiting for LNB's site, sending them again", core.pending.size)
        }
    }

    fun onServerStopped(event: ServerStoppedEvent) {
        core = null
        server = null
    }

    fun onServerTick(event: ServerTickEvent.Post) {
        if (++ticks < TICKS_BETWEEN_CHECKS) return
        ticks = 0
        core?.tick()
    }

    private fun finish(entry: JournalEntry, outcome: Outcome) {
        val server = server ?: return
        val handler = handlers[entry.purpose]
        if (handler == null) {
            LnbEconomy.LOGGER.error("No journal handler for {}: entry {} ended with {}", entry.purpose, entry.key, outcome)
            return
        }
        try {
            handler.onOutcome(server, entry, outcome)
        } catch (e: Exception) {
            LnbEconomy.LOGGER.error("Journal handler {} failed for entry {}", entry.purpose, entry.key, e)
        }
    }
}
