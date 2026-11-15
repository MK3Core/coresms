package io.github.mk3core.coresms.data

import android.telephony.PhoneNumberUtils

/** Comparing and cleaning up phone numbers (and the occasional email address). */
object Phone {

    /** True if both refer to the same number, e.g. "+1 (555) 123-4567" and "5551234567". */
    fun same(a: String, b: String): Boolean {
        if (a.isEmail() || b.isEmail()) return a.trim().equals(b.trim(), ignoreCase = true)
        return PhoneNumberUtils.compare(a, b)
    }

    /** Strips formatting so numbers typed by hand match stored ones. Emails pass through. */
    fun clean(address: String): String {
        val trimmed = address.trim()
        if (trimmed.isEmail()) return trimmed
        return trimmed.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
    }

    /** True for strings that could be a recipient: a phone number, short code or email. */
    fun isValidRecipient(address: String): Boolean {
        val trimmed = address.trim()
        if (trimmed.isEmail()) return true
        return clean(trimmed).count { it.isDigit() } >= 3
    }

    /** Removes our own numbers and duplicates (by number, not by text). */
    fun others(addresses: Collection<String>, self: Collection<String>): List<String> {
        val result = mutableListOf<String>()
        for (address in addresses) {
            if (address.isBlank()) continue
            if (self.any { same(it, address) }) continue
            if (result.any { same(it, address) }) continue
            result += address
        }
        return result
    }

    private fun String.isEmail() = contains('@')
}
