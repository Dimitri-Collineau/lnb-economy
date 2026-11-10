package fr.bde_teknantes.lnb.economy.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandlesTest {
    @Test
    fun `handles need their hash and the site's alphabet`() {
        assertTrue(Handles.isHandle("#casino-royal"))
        assertFalse(Handles.isHandle("casino-royal"))
        assertFalse(Handles.isHandle("#Casino"))
        assertFalse(Handles.isHandle("#ab"))
        assertFalse(Handles.isHandle("#" + "a".repeat(25)))
    }

    @Test
    fun `display adds exactly one hash`() {
        assertEquals("#casino", Handles.display("casino"))
        assertEquals("#casino", Handles.display("#casino"))
        assertEquals("casino", Handles.bare("#casino"))
    }
}
