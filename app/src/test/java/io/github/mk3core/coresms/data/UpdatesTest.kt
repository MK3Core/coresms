package io.github.mk3core.coresms.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {

    @Test fun shortCodesAreAutomated() {
        listOf("72975", "262966", "4321", "+22000").forEach { assertTrue(it, Updates.isAutomatedSender(it)) }
    }

    @Test fun namedSendersAreAutomated() {
        listOf("GOOGLE", "Chase", "AMZN-Alert").forEach { assertTrue(it, Updates.isAutomatedSender(it)) }
    }

    @Test fun peopleAreNotAutomated() {
        listOf("+15555550123", "5555550123", "(555) 555-0123", "someone@example.com", "", "12")
            .forEach { assertFalse(it, Updates.isAutomatedSender(it)) }
    }

    @Test fun tollFree() {
        assertTrue(Updates.isTollFree("+18005550100"))
        assertTrue(Updates.isTollFree("(888) 555-0100"))
        assertFalse(Updates.isTollFree("+15555550123"))
        assertFalse(Updates.isTollFree("72975"))
        assertFalse(Updates.isTollFree("+448005550100"))
    }

    @Test fun keysIgnoreFormatting() {
        assertEquals(Updates.addressKey("+1 (555) 555-0123"), Updates.addressKey("5555550123"))
        assertEquals("google", Updates.addressKey("GOOGLE"))
        assertEquals("72975", Updates.addressKey("72975"))
    }

    @Test fun groupKeysDontDependOnOrder() {
        val a = Contact("+15555550123", null, null)
        val b = Contact("5555550199", "B", null)
        assertEquals(Updates.key(listOf(a, b)), Updates.key(listOf(b, a)))
    }
}
