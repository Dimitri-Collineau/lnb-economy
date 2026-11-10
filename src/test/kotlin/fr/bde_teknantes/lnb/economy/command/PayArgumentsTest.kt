package fr.bde_teknantes.lnb.economy.command

import fr.bde_teknantes.lnb.economy.command.PayArguments.Result
import fr.bde_teknantes.lnb.economy.command.PayArguments.Target
import kotlin.test.Test
import kotlin.test.assertEquals

class PayArgumentsTest {
    private fun ok(input: String) = (PayArguments.parse(input) as Result.Ok).value
    private fun error(input: String) = (PayArguments.parse(input) as Result.Error).key

    @Test
    fun `a player, an amount and a message`() {
        val parsed = ok("Steve 50 for the diamonds")
        assertEquals(Target.Player("Steve"), parsed.target)
        assertEquals(50, parsed.amount)
        assertEquals("for the diamonds", parsed.memo)
    }

    @Test
    fun `a handle needs its hash`() {
        assertEquals(Target.Account("casino-royal"), ok("#casino-royal 10").target)
        assertEquals(null, ok("#casino-royal 10").memo)
    }

    @Test
    fun `bad input names what is wrong`() {
        assertEquals("economy.lnbeconomy.pay.usage", error("Steve"))
        assertEquals("economy.lnbeconomy.pay.bad_amount", error("Steve 0"))
        assertEquals("economy.lnbeconomy.pay.bad_amount", error("Steve 1.5"))
        assertEquals("economy.lnbeconomy.pay.bad_amount", error("Steve 1000000000001"))
        assertEquals("economy.lnbeconomy.pay.bad_handle", error("#A 10"))
        assertEquals("economy.lnbeconomy.pay.bad_player", error("not-a-name 10"))
        assertEquals("economy.lnbeconomy.pay.memo_too_long", error("Steve 10 " + "x".repeat(141)))
    }
}
