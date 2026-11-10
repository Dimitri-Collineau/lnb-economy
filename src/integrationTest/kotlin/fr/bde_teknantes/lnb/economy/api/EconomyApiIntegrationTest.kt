package fr.bde_teknantes.lnb.economy.api

import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Calls a running LNB site (see README). Skipped unless these are set:
 *
 * - `LNB_API_URL` and `LNB_API_TOKEN`: the site and its `WARDEN_SERVER_SECRET`;
 * - `LNB_TEST_PLAYER`: the UUID of a Minecraft account linked to an eligible LNB account, with at least 1 LNBit;
 * - `LNB_TEST_CASINO`: the handle of a shared account that player owns or is a member of.
 *
 * Moves 1 LNBit to the shared account and back.
 */
class EconomyApiIntegrationTest {
    private lateinit var player: UUID
    private lateinit var casino: String

    @BeforeEach
    fun connect() {
        val url = System.getenv("LNB_API_URL")
        val token = System.getenv("LNB_API_TOKEN")
        val player = System.getenv("LNB_TEST_PLAYER")
        val casino = System.getenv("LNB_TEST_CASINO")
        assumeTrue(listOf(url, token, player, casino).none { it.isNullOrBlank() }, "LNB_API_URL, LNB_API_TOKEN, LNB_TEST_PLAYER and LNB_TEST_CASINO aren't all set")
        EconomyApi.connection = { url to token }
        this.player = UUID.fromString(player)
        this.casino = Handles.bare(casino)
    }

    private fun <T> ApiResult<T>.ok(): T = assertIs<ApiResult.Ok<T>>(this, describe()).body

    @Test
    fun `a linked player has a personal account first`() {
        val found = EconomyApi.player(player).get().ok()
        assertTrue(found.accounts.first().isPersonal)
        assertNotNull(found.defaultAccount)
    }

    @Test
    fun `an unlinked uuid is notLinked`() {
        val result = EconomyApi.player(UUID.nameUUIDFromBytes("OfflinePlayer:nobody-${UUID.randomUUID()}".toByteArray())).get()
        assertEquals(ApiResult.Http(404, Problems.NOT_LINKED), result)
    }

    @Test
    fun `a shared account is found with or without its hash`() {
        assertEquals(casino, EconomyApi.account(casino).get().ok().handle)
        assertEquals(casino, EconomyApi.account("#$casino").get().ok().handle)
    }

    @Test
    fun `setting the default account answers with the player's accounts`() {
        val before = EconomyApi.player(player).get().ok()
        val shared = EconomyApi.setDefaultAccount(player, casino).get().ok()
        assertEquals(EconomyApi.account(casino).get().ok().id, shared.defaultAccountId)
        val personal = EconomyApi.setDefaultAccount(player, before.accounts.first().id).get().ok()
        assertEquals(before.accounts.first().id, personal.defaultAccountId)
        val restored = EconomyApi.setDefaultAccount(player, before.defaultAccountId).get().ok()
        assertEquals(before.defaultAccountId, restored.defaultAccountId)
    }

    @Test
    fun `a transfer sent twice moves once, and a casino pays back without a player`() {
        val bet = TransferBody(UUID.randomUUID().toString(), Party(player = player.toString()), Party(account = casino), 1, TransferBody.KIND_CASINO, "lnb-economy integration test")
        val first = EconomyApi.transfer(bet).get().ok()
        val again = EconomyApi.transfer(bet).get().ok()
        assertEquals(false, first.replayed)
        assertEquals(true, again.replayed)
        assertEquals(first.transactionId, again.transactionId)

        val mismatch = EconomyApi.transfer(bet.copy(amount = 2)).get()
        assertEquals(ApiResult.Http(409, Problems.IDEMPOTENCY_MISMATCH), mismatch)

        val payout = TransferBody(UUID.randomUUID().toString(), Party(account = casino), Party(player = player.toString()), 1, TransferBody.KIND_CASINO, "lnb-economy integration test")
        EconomyApi.transfer(payout).get().ok()
    }
}
