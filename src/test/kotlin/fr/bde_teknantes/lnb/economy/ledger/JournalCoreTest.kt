package fr.bde_teknantes.lnb.economy.ledger

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import fr.bde_teknantes.lnb.economy.api.Party
import fr.bde_teknantes.lnb.economy.api.TransferBody
import fr.bde_teknantes.lnb.economy.api.TransferResult
import fr.bde_teknantes.lnb.economy.api.TransferSide
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
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
    private val sent = mutableListOf<JournalEntry>()
    private val answers = ArrayDeque<ApiResult<JsonElement>>()
    private val outcomes = mutableListOf<Pair<JournalEntry, Outcome>>()

    private fun core(store: JournalStore = JournalStore(dir.resolve("journal.json"))) = JournalCore(
        store = store,
        send = { entry ->
            sent.add(entry)
            CompletableFuture.completedFuture(answers.removeFirst())
        },
        clock = { now },
        executor = { it.run() },
        onOutcome = { entry, outcome -> outcomes.add(entry to outcome) },
    )

    private fun body(key: String = "7b0f4a3e-6d1c-4b8e-9f2a-1c3d5e7f9a0b") =
        TransferBody(key, Party(player = "uuid"), Party(account = "casino"), 250, TransferBody.KIND_CASINO)

    private fun ok(body: TransferBody): ApiResult<JsonElement> = ApiResult.Ok(
        ApiClient.GSON.toJsonTree(TransferResult(1, false, body.amount, TransferSide("a", null, 0), TransferSide("b", "casino", 250))),
    )

    private fun entry(body: TransferBody = body(), data: Map<String, String> = emptyMap()) =
        JournalEntry.of(JournalEntry.TRANSFERS, body, "test", data)

    @Test
    fun `a confirmed transfer leaves the journal`() {
        val core = core()
        answers.add(ok(body()))
        core.submit(entry())

        assertEquals(1, sent.size)
        assertTrue(core.pending.isEmpty())
        val done = assertIs<Outcome.Done>(outcomes.single().second)
        assertEquals(250, done.result.amount)
    }

    @Test
    fun `a refused transfer leaves the journal and is not sent again`() {
        val core = core()
        answers.add(ApiResult.Http(409, "insufficientFunds"))
        core.submit(entry())
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
        core.submit(entry())
        assertIs<Outcome.Delayed>(outcomes.single().second)

        core.tick()
        assertEquals(1, sent.size, "not before the delay")

        now += RetryPolicy.delayAfter(1)
        answers.add(ok(body()))
        core.tick()

        assertEquals(2, sent.size)
        assertEquals(sent[0].key, sent[1].key)
        assertIs<Outcome.Done>(outcomes.last().second)
        assertTrue(core.pending.isEmpty())
    }

    @Test
    fun `only the first failure is reported`() {
        val core = core()
        repeat(3) { answers.add(ApiResult.Http(502, null)) }
        core.submit(entry())
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
        core(store).submit(entry(data = mapOf("chips" to "25")))

        val restarted = core(store)
        restarted.load()
        val entry = restarted.pending.single()
        assertEquals("test", entry.purpose)
        assertEquals(mapOf("chips" to "25"), entry.data)
        assertEquals(body(), entry.transfer)

        answers.add(ok(body()))
        restarted.tick()
        assertEquals(2, sent.size)
        assertTrue(restarted.pending.isEmpty())
        assertTrue(JournalStore(dir.resolve("journal.json")).load().isEmpty())
    }

    @Test
    fun `an entry for another route is sent to it with its body as is`() {
        val core = core()
        answers.add(ApiResult.Ok(JsonObject().apply { addProperty("orderId", 7) }))
        core.submit(JournalEntry.of("/shops/s1/orders", Order("k1", "buy", 3), "test"))

        val entry = sent.single()
        assertEquals("/shops/s1/orders", entry.route)
        assertEquals("k1", entry.key)
        assertEquals(null, entry.transfer)
        assertEquals(3, entry.body.get("lots").asInt)
        assertEquals(7, assertIs<Outcome.Done>(outcomes.single().second).decode<OrderAnswer>().orderId)
    }

    @Test
    fun `entries written when the journal only sent transfers are read as transfers`() {
        val file = dir.resolve("journal.json")
        Files.writeString(
            file,
            """[{"body":{"idempotencyKey":"old-key","from":{"player":"uuid"},"to":{"account":"casino"},"amount":250,"kind":"casino"},""" +
                """"purpose":"test","data":{"chips":"25"},"attempts":2,"nextAttemptAt":0,"createdAt":5}]""",
        )
        val core = core(JournalStore(file))
        core.load()

        val entry = core.pending.single()
        assertEquals(JournalEntry.TRANSFERS, entry.route)
        assertEquals("old-key", entry.key)
        assertEquals(250, entry.transfer?.amount)
        assertEquals(mapOf("chips" to "25"), entry.data)
    }

    @Test
    fun `an entry without an idempotency key is refused`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { JournalEntry.of("/x", mapOf("amount" to 1), "test") }
    }

    private data class Order(val idempotencyKey: String, val direction: String, val lots: Int)

    private data class OrderAnswer(val orderId: Long)
}
