package fr.bde_teknantes.lnb.economy.ledger

import fr.bde_teknantes.lnb.economy.api.TransferBody
import fr.bde_teknantes.lnb.economy.api.TransferResult
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import java.util.concurrent.CompletableFuture

/** The end of a journal entry, or news about it. */
sealed interface Outcome {
    data class Done(val result: TransferResult) : Outcome

    data class Refused(val result: ApiResult<*>) : Outcome

    /** The first attempt got no answer; the journal keeps trying. Sent once per entry. */
    data class Delayed(val result: ApiResult<*>) : Outcome
}

/**
 * The journal's logic, without Minecraft: what to send, when, and what each answer means.
 *
 * Not thread-safe: every method, and [onOutcome], runs on one thread (the server thread), and answers are brought
 * back to it by [executor].
 */
class JournalCore(
    private val store: JournalStore,
    private val send: (TransferBody) -> CompletableFuture<ApiResult<TransferResult>>,
    private val clock: () -> Long,
    private val executor: (Runnable) -> Unit,
    private val onOutcome: (JournalEntry, Outcome) -> Unit,
) {
    private val entries = LinkedHashMap<String, JournalEntry>()
    private val inFlight = HashSet<String>()

    val pending: List<JournalEntry> get() = entries.values.toList()

    fun load() {
        entries.clear()
        store.load().forEach { entries[it.key] = it.copy(nextAttemptAt = 0) }
    }

    /** Writes [entry] to disk, then sends it. */
    fun submit(entry: JournalEntry) {
        require(entry.key !in entries) { "Idempotency key ${entry.key} is already in the journal" }
        entries[entry.key] = entry.copy(createdAt = clock())
        store.save(entries.values)
        attempt(entry.key)
    }

    /** Sends the entries whose retry time has come. */
    fun tick() {
        val now = clock()
        entries.values.filter { it.key !in inFlight && it.nextAttemptAt <= now }.forEach { attempt(it.key) }
    }

    /** Sends an entry again now, whatever its retry time. Returns false when there is no such entry. */
    fun retryNow(key: String): Boolean {
        val entry = entries[key] ?: return false
        if (key !in inFlight) {
            entries[key] = entry.copy(nextAttemptAt = 0)
            attempt(key)
        }
        return true
    }

    private fun attempt(key: String) {
        val entry = entries[key] ?: return
        inFlight.add(key)
        send(entry.body).whenComplete { result, error ->
            val answer = result ?: ApiResult.Network(error ?: IllegalStateException("No answer"))
            executor(Runnable { answered(key, answer) })
        }
    }

    private fun answered(key: String, result: ApiResult<TransferResult>) {
        inFlight.remove(key)
        val entry = entries[key] ?: return
        when (RetryPolicy.verdict(result)) {
            Verdict.DONE -> {
                entries.remove(key)
                store.save(entries.values)
                onOutcome(entry, Outcome.Done((result as ApiResult.Ok).body))
            }
            Verdict.REFUSED -> {
                entries.remove(key)
                store.save(entries.values)
                onOutcome(entry, Outcome.Refused(result))
            }
            Verdict.RETRY -> {
                val attempts = entry.attempts + 1
                val updated = entry.copy(
                    attempts = attempts,
                    nextAttemptAt = clock() + RetryPolicy.delayAfter(attempts),
                    lastError = result.describe(),
                )
                entries[key] = updated
                store.save(entries.values)
                if (attempts == 1) onOutcome(updated, Outcome.Delayed(result))
            }
        }
    }
}
