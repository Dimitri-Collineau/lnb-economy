package fr.bde_teknantes.lnb.economy.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountRulesTest {
    @Test
    fun `accepts a handle with or without its hash`() {
        assertNull(AccountRules.handleProblem("casino-royal"))
        assertNull(AccountRules.handleProblem("#casino-royal"))
    }

    @Test
    fun `refuses handles that are too short, too long or not lower case`() {
        assertNotNull(AccountRules.handleProblem("ab"))
        assertNotNull(AccountRules.handleProblem("a".repeat(25)))
        assertNotNull(AccountRules.handleProblem("Casino"))
        assertNotNull(AccountRules.handleProblem("casino royal"))
    }

    @Test
    fun `a name is 1 to 32 characters once trimmed`() {
        assertNull(AccountRules.nameProblem("  Casino Royal  "))
        assertNull(AccountRules.nameProblem("a".repeat(32)))
        assertNotNull(AccountRules.nameProblem("   "))
        assertNotNull(AccountRules.nameProblem("a".repeat(33)))
    }

    @Test
    fun `only the three known types pass`() {
        assertNull(AccountRules.typeProblem("team"))
        assertNull(AccountRules.typeProblem("shop"))
        assertNull(AccountRules.typeProblem("casino"))
        assertNotNull(AccountRules.typeProblem("bank"))
    }

    @Test
    fun `usernames are 3 to 16 letters, digits or underscores`() {
        assertTrue(AccountRules.isUsername("Lnb_Tester42"))
        assertFalse(AccountRules.isUsername("ab"))
        assertFalse(AccountRules.isUsername("a".repeat(17)))
        assertFalse(AccountRules.isUsername("with space"))
    }

    @Test
    fun `suggests a handle from a name`() {
        assertEquals("casino-royal", HandleSuggestion.of("Casino Royal !"))
        assertEquals("cafe-de-l-ete", HandleSuggestion.of("Café de l'été"))
        assertEquals("", HandleSuggestion.of("!!!"))
        assertEquals(24, HandleSuggestion.of("a".repeat(40)).length)
        assertEquals("abc", HandleSuggestion.of("abc" + " ".repeat(30) + "d").take(3))
    }
}
