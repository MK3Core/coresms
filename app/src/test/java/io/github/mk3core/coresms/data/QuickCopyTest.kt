package io.github.mk3core.coresms.data

import io.github.mk3core.coresms.data.CopyTarget.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickCopyTest {

    private fun codes(text: String) = QuickCopy.find(text).filter { it.kind == Kind.Code }.map { it.text }
    private fun addresses(text: String) = QuickCopy.find(text).filter { it.kind == Kind.Address }.map { it.text }

    @Test fun commonCodeMessages() {
        assertEquals(listOf("482913"), codes("G-482913 is your Google verification code."))
        assertEquals(listOf("123456"), codes("Your verification code is 123456"))
        assertEquals(listOf("4821"), codes("4821 is your Venmo login code. Don't share it."))
        assertEquals(listOf("123456"), codes("Your Amazon OTP: 123-456"))
        assertEquals(listOf("987654"), codes("Use 987 654 to sign in. Expires in 10 minutes."))
        assertEquals(listOf("K7P2Q9"), codes("Your one-time passcode is K7P2Q9"))
        assertEquals(
            listOf("552018"),
            codes("[Chase] Your security code is 552018. It expires in 10 minutes. Call 1-800-935-9935 if you didn't request it."),
        )
        assertEquals(listOf("330711"), codes("Your code: 330711\n\n@example.com #330711"))
    }

    @Test fun ignoresNumbersWithoutCodeWords() {
        assertEquals(emptyList<String>(), codes("I'll be there at 1830, see you"))
        assertEquals(emptyList<String>(), codes("The total was 2450 for the trip"))
    }

    @Test fun ignoresPricesPhonesDatesAndLinks() {
        assertEquals(emptyList<String>(), codes("The code costs $1999 now"))
        assertEquals(emptyList<String>(), codes("Call (555) 123-4567 for your code"))
        assertEquals(emptyList<String>(), codes("Confirmation for 11/15/2026 at 10:30"))
        assertEquals(emptyList<String>(), codes("Verify at https://example.com/v/123456"))
        assertEquals(emptyList<String>(), codes("Save 2500% with code"))
    }

    @Test fun ignoresYearsInAppointmentReminders() {
        assertEquals(emptyList<String>(), codes("Please confirm your visit on Mon Apr 20 2026, 1:00pm. Reply Y to confirm."))
        assertEquals(emptyList<String>(), codes("Text YES to confirm your appt on Monday, June 15, 2026."))
        assertEquals(listOf("2026"), codes("[Uber] Your code: 2026. Never share this code."))
    }

    @Test fun commonAddresses() {
        assertEquals(listOf("123 Main St"), addresses("Meet at 123 Main St tonight"))
        assertEquals(
            listOf("4500 N Lincoln Ave, Apt 2B, Chicago, IL 60625"),
            addresses("New place is 4500 N Lincoln Ave, Apt 2B, Chicago, IL 60625!"),
        )
        assertEquals(listOf("1600 Pennsylvania Avenue NW, Washington, DC 20500"),
            addresses("1600 Pennsylvania Avenue NW, Washington, DC 20500"))
        assertEquals(listOf("221 W 42nd Street"), addresses("Party at 221 W 42nd Street."))
        assertEquals(listOf("12 oak st"), addresses("its 12 oak st"))
        assertEquals(listOf("90 Cedar Hollow Way 37027"), addresses("Ship to 90 Cedar Hollow Way 37027"))
        assertEquals(listOf("8 Elm Ct"), addresses("8 Elm Ct or 9 Elm Ct?").take(1))
        assertEquals(listOf("700 Market St, San Francisco, CA"), addresses("700 Market St, San Francisco, CA"))
        assertEquals(
            listOf("410 Harbor Ave, Springfield, Illinois 62701"),
            addresses("See you at 410 Harbor Ave, Springfield, Illinois 62701. Arrive early."),
        )
        assertEquals(listOf("880 S Maple St Ste 12 Springfield, IL 62704"),
            addresses("Ready at 880 S Maple St Ste 12\nSpringfield, IL 62704"))
    }

    @Test fun lowercaseStateWordsAreNotStates() {
        assertEquals(listOf("123 Main St"), addresses("Meet at 123 Main St and Oak or after"))
    }

    @Test fun ignoresEverydayPhrasesThatLookLikeAddresses() {
        assertEquals(emptyList<String>(), addresses("It's about a 10 minutes drive from here"))
        assertEquals(emptyList<String>(), addresses("We walked 5 miles down the road"))
        assertEquals(emptyList<String>(), addresses("I have 2 more days left"))
        assertEquals(emptyList<String>(), addresses("there are 3 ways to do it"))
        assertEquals(emptyList<String>(), addresses("We went 2 blocks out of the way"))
        assertEquals(emptyList<String>(), addresses("Ok. Making a 9 Coffee run back to the shop by 1.30"))
    }

    @Test fun nothingInPlainChatter() {
        assertEquals(emptyList<CopyTarget>(), QuickCopy.find("love you, see you soon ❤️"))
        assertEquals(emptyList<CopyTarget>(), QuickCopy.find(""))
    }
}
