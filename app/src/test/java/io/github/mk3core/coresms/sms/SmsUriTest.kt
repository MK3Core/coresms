package io.github.mk3core.coresms.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsUriTest {

    @Test
    fun singleRecipient() {
        assertEquals(listOf("+15551234567"), SmsUri.recipients("+15551234567"))
    }

    @Test
    fun multipleRecipientsWithCommasOrSemicolons() {
        assertEquals(
            listOf("5551234567", "5559876543", "+15550001111"),
            SmsUri.recipients("5551234567, 5559876543;+15550001111"),
        )
    }

    @Test
    fun duplicatesAndBlanksDropped() {
        assertEquals(listOf("555", "666"), SmsUri.recipients("555,,555; ;666"))
    }

    @Test
    fun queryIsNotARecipient() {
        assertEquals(listOf("5551234567"), SmsUri.recipients("5551234567?body=Hi there"))
    }

    @Test
    fun emptyOrMissing() {
        assertEquals(emptyList<String>(), SmsUri.recipients(null))
        assertEquals(emptyList<String>(), SmsUri.recipients(""))
    }

    @Test
    fun bodyFromQuery() {
        assertEquals("Running late", SmsUri.body("5551234567?body=Running late"))
        assertEquals("Hi", SmsUri.body("555?foo=1&body=Hi"))
    }

    @Test
    fun noBody() {
        assertNull(SmsUri.body("5551234567"))
        assertNull(SmsUri.body("5551234567?body="))
        assertNull(SmsUri.body(null))
    }
}
