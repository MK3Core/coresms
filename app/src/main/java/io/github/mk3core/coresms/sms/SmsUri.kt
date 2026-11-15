package io.github.mk3core.coresms.sms

/**
 * Reads sms:, smsto:, mms: and mmsto: links such as
 * "smsto:+15551234567,5559876543?body=Running late".
 *
 * Takes the part after the scheme (Uri.getSchemeSpecificPart, already
 * decoded), so it can be unit tested without Android's Uri class.
 */
object SmsUri {

    /** Recipients, split on commas or semicolons, in order, without duplicates. */
    fun recipients(schemeSpecificPart: String?): List<String> =
        schemeSpecificPart.orEmpty()
            .substringBefore('?')
            .split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** The prefilled message from a "?body=" parameter, if there is one. */
    fun body(schemeSpecificPart: String?): String? =
        schemeSpecificPart
            ?.substringAfter('?', missingDelimiterValue = "")
            ?.split('&')
            ?.firstOrNull { it.startsWith("body=") }
            ?.removePrefix("body=")
            ?.takeIf { it.isNotEmpty() }
}
