package fr.bde_teknantes.lnb.economy.ledger

import fr.bde_teknantes.lnb.economy.api.Party
import fr.bde_teknantes.lnb.economy.api.TransferBody
import fr.bde_teknantes.lnb.economy.api.TransferResult
import fr.bde_teknantes.lnb.economy.api.TransferSide
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JournalCoreTest {
    @TempDir
    lateinit var dir: Path

    private var now = 1_000L
    private val sent = mutableListOf<TransferBody>()
    private val answers = ArrayDeque<ApiResult<TransferResult>>()
    private val outcomes = mutableListOf<Pair<JournalEntry, Outcome>>()

    private fun core(store: JournalStore = JournalStore(dir.resolve("journal.json"))) = JournalCore(
        store = store,
        send = { body ->
            sent.add(body)
            CompletableFuture.completedFuture(answers.removeFirst())
        },
        clock = { now },
        executor = { it.run() },
        onOutcome = { entry, outcome -> outcomes.add(entry to outcome) },
    )

    private fun body(key: String = "7b0f4a3e-6d1c-4b8e-9f2a-1c3d5e7f9a0b") =
        TransferBody(key, Party(player = "uuid"), Party(account = "casino"), 250, TransferBody.KIND_CASINO)

    private fun ok(body: TransferBody) = ApiResult.Ok(
        TransferResult(1, false, body.amount, TransferSide("a", null, 0), TransferSide("b", "casino", 250)),
    )

    @Test
    fun `a confirmed transfer leaves the journal`() {
        val core = core()
        answers.add(ok(body()))
        core.submit(JournalEntry(body(), "test"))

        assertEquals(1, sent.size)
        assertTrue(core.pending.isEmpty())
        assertIs<Outcome.Done>(outcomes.single().second)
    }

    @Test
    fun `a refused transfer leaves the journal and is not sent again`() {
        val core = core()
        answers.add(ApiResult.Http(409, "insufficientFunds"))
        core.submit(JournalEntry(body(), "test"))
        now += 1_000_000
        core.tick()

        assertEquals(1, sent.size)
        assertTrue(core.pending.isEmpty())
        assertIs<Outcome.Refused>(outcomes.single().second)
    }

    @Test
    fun `an unanswered transfer is sent again with the same key after its delay`() {
        val core = core()
        answers.add(ApiResult.Network(RuntimeException("timeout")))
        core.submit(JournalEntry(body(), "test"))
        assertIs<Outcome.Delayed>(outcomes.single().second)

        core.tick()
        assertEquals(1, sent.size, "not before the delay")

        now += RetryPolicy.delayAfter(1)
        answers.add(ok(body()))
        core.tick()

        assertEquals(2, sent.size)
        assertEquals(sent[0].idempotencyKey, sent[1].idempotencyKey)
        assertIs<Outcome.Done>(outcomes.last().second)
        assertTrue(core.pending.isEmpty())
    }

    @Test
    fun `only the first failure is reported`() {
        val core = core()
        repeat(3) { answers.add(ApiResult.Http(502, null)) }
        core.submit(JournalEntry(body(), "test"))
        repeat(2) {
            now += 1_000_000
            core.tick()
        }
        assertEquals(3, sent.size)
        assertEquals(1, outcomes.size)
        assertEquals(3, core.pending.single().attempts)
    }

    @Test
    fun `pending transfers survive a restart and are sent right away`() {
        val store = JournalStore(dir.resolve("journal.json"))
        answers.add(ApiResult.Network(RuntimeException("down")))
        core(store).submit(JournalEntry(body(), "test", mapOf("chips" to "25")))

        val restarted = core(store)
        restarted.load()
        val entry = restarted.pending.single()
        assertEquals("test", entry.purpose)
        assertEquals(mapOf("chips" to "25"), entry.data)
        assertEquals(body(), entry.body)

        answers.add(ok(body()))
        restarted.tick()
        assertEquals(2, sent.size)
        assertTrue(restarted.pending.isEmpty())
        assertTrue(JournalStore(dir.resolve("journal.json")).load().isEmpty())
    }
}
