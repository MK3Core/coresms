package io.github.mk3core.coresms.data

/** Something in a message worth a one-tap copy button. */
data class CopyTarget(val kind: Kind, val text: String) {
    enum class Kind { Code, Address }
}

/**
 * Finds verification codes and US street addresses in message text.
 * Both lean toward missing one over offering a button on ordinary chatter:
 * a code needs a word like "code" or "verify" nearby, and an address needs
 * a house number, a street name and a street type like "St" or "Avenue".
 */
object QuickCopy {

    fun find(body: String): List<CopyTarget> {
        if (body.isBlank()) return emptyList()
        val text = URL.replace(body) { " ".repeat(it.value.length) }
        return listOfNotNull(code(text)) + addresses(text).take(2)
    }

    // --- Verification codes ---

    private val CODE_WORDS = Regex(
        """\b(codes?|verification|verify|verifying|passcode|password|pin|otp|one[- ]time|2fa|two[- ]factor|authenticat\w*|security|log ?in|sign[- ]?in|confirmation|confirm)\b""",
        RegexOption.IGNORE_CASE,
    )

    // 4-8 digits, or two equal groups ("123-456", "1234 5678"; not a phone's "123-4567"), optionally with a
    // letter prefix like Google's "G-". Or a short uppercase letters-and-digits code.
    private val NUMERIC = Regex("""\b(?:[A-Z]{1,4}-)?(\d{3}[- ]\d{3}|\d{4}[- ]\d{4}|\d{4,8})\b""")
    private val ALPHANUMERIC = Regex("""\b(?=[A-Z0-9]*\d)(?=[A-Z0-9]*[A-Z])[A-Z0-9]{5,8}\b""")

    private fun code(text: String): CopyTarget? {
        val keywords = CODE_WORDS.findAll(text).map { it.range }.toList()
        if (keywords.isEmpty()) return null
        val candidates = NUMERIC.findAll(text).filter { standsAlone(text, it.range) && !isYearInDate(text, it.range) }
            .map { it.range to it.groupValues[1].filter(Char::isDigit) }
            .toMutableList()
        if (candidates.isEmpty()) {
            ALPHANUMERIC.findAll(text).filter { standsAlone(text, it.range) }
                .mapTo(candidates) { it.range to it.value }
        }
        val best = candidates.minByOrNull { (range, _) -> keywords.minOf { distance(range, it) } } ?: return null
        return CopyTarget(CopyTarget.Kind.Code, best.second)
    }

    /** Not part of a phone number, price, date, time or percentage. */
    private fun standsAlone(text: String, range: IntRange): Boolean {
        val before = text.getOrNull(range.first - 1)
        val after = text.getOrNull(range.last + 1)
        val afterNext = text.getOrNull(range.last + 2)
        if (before != null && before in "$€£#()/.:-+") return false
        if (after == '%' || after == ')') return false
        if (after != null && after in "-/.:," && afterNext?.isDigit() == true) return false
        return true
    }

    private val DATE_BEFORE_YEAR = Regex(
        """(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\.?\s+(?:\d{1,2}(?:st|nd|rd|th)?,?\s+)?$""",
        RegexOption.IGNORE_CASE,
    )

    /** "Apr 20 2026" or "June 15, 2026": a year in a date, common in appointment texts that say "confirm". */
    private fun isYearInDate(text: String, range: IntRange): Boolean {
        val number = text.substring(range)
        if (number.length != 4 || !(number.startsWith("19") || number.startsWith("20"))) return false
        return DATE_BEFORE_YEAR.containsMatchIn(text.substring(maxOf(0, range.first - 16), range.first))
    }

    private fun distance(a: IntRange, b: IntRange): Int = when {
        a.last < b.first -> b.first - a.last
        b.last < a.first -> a.first - b.last
        else -> 0
    }

    // --- US street addresses ---

    private val ABBREVIATED_TYPES = listOf(
        "St", "Ave", "Av", "Rd", "Blvd", "Dr", "Ln", "Ct", "Pl", "Pkwy", "Hwy", "Cir", "Ter", "Trl", "Sq", "Cv", "Xing",
    )
    private val SPELLED_TYPES = listOf(
        "Street", "Avenue", "Road", "Boulevard", "Drive", "Lane", "Court", "Place", "Parkway", "Highway", "Circle",
        "Terrace", "Trail", "Way", "Square", "Loop", "Run", "Pike", "Path", "Row", "Crossing", "Cove", "Alley", "Plaza",
    )
    private val STATE_NAMES = listOf(
        "Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado", "Connecticut", "Delaware", "Florida",
        "Georgia", "Hawaii", "Idaho", "Illinois", "Indiana", "Iowa", "Kansas", "Kentucky", "Louisiana", "Maine",
        "Maryland", "Massachusetts", "Michigan", "Minnesota", "Mississippi", "Missouri", "Montana", "Nebraska",
        "Nevada", "New Hampshire", "New Jersey", "New Mexico", "New York", "North Carolina", "North Dakota", "Ohio",
        "Oklahoma", "Oregon", "Pennsylvania", "Rhode Island", "South Carolina", "South Dakota", "Tennessee", "Texas",
        "Utah", "Vermont", "Virginia", "Washington", "West Virginia", "Wisconsin", "Wyoming",
    )
    private val STATES = (
        "AL AK AZ AR CA CO CT DE DC FL GA HI ID IL IN IA KS KY LA ME MD MA MI MN MS MO MT NE NV NH NJ NM NY NC ND " +
            "OH OK OR PA RI SC SD TN TX UT VT VA WA WV WI WY PR"
        ).split(' ')

    private const val DIRECTION = """(?:N|S|E|W|NE|NW|SE|SW|North|South|East|West)\.?"""
    private const val WORD = """(?:\d{1,3}(?:st|nd|rd|th)|[A-Za-z][A-Za-z'.-]*)"""

    private val ADDRESS = Regex(
        // The street is matched in any case; the city and state must be
        // capitalized, or words like "in" and "or" would pass for states.
        """(?<![\w$#.-])(\d{1,6}[A-Za-z]?)\s+""" +
            """(?i:((?:$DIRECTION\s+)?(?:$WORD\s+){0,3}$WORD)\s+""" +
            """(${(ABBREVIATED_TYPES + SPELLED_TYPES).joinToString("|")})\b\.?""" +
            """(?:\s+$DIRECTION(?![A-Za-z]))?""" +
            """(?:,?\s*(?:Apt|Apartment|Suite|Ste|Unit|Bldg|Building|Fl|Floor|Rm|Room|#)\.?\s*#?\s*[A-Za-z0-9-]+)?)""" +
            """(?:,?\s+(?:[A-Z][A-Za-z.'-]*\s+){0,2}[A-Z][A-Za-z.'-]*,?\s+(?:${(STATE_NAMES + STATES).joinToString("|")})\b(?:\s+\d{5}(?:-\d{4})?)?""" +
            """|\s+\d{5}(?:-\d{4})?\b)?""",
    )

    private val NOT_STREET_NAMES = setOf(
        "a", "an", "the", "my", "your", "his", "her", "our", "their", "its", "this", "that", "these", "those",
        "to", "of", "in", "on", "at", "by", "for", "from", "with", "and", "or", "but", "so", "all", "no", "more",
        "other", "some", "any", "is", "was", "are", "be", "been", "it", "i", "you", "we", "they", "he", "she",
        "me", "us", "them", "out", "up", "down", "into", "over", "one", "two", "three", "half", "next", "last",
        "minutes", "minute", "hours", "hour", "mins", "min", "miles", "mile", "days", "day", "weeks", "years",
        "times", "blocks", "feet", "ft", "steps",
    )

    private fun addresses(text: String): List<CopyTarget> = ADDRESS.findAll(text).mapNotNull { match ->
        val words = match.groupValues[2].split(Regex("\\s+"))
        val type = match.groupValues[3]
        if (words.any { it.lowercase().trimEnd('.') in NOT_STREET_NAMES }) return@mapNotNull null
        // Typed casually in lowercase, it still needs a short street type
        // ("12 main st") so "10 minutes drive" isn't taken for an address.
        val casual = words.any { it.first().isLetter() && it.first().isLowerCase() }
        val abbreviated = ABBREVIATED_TYPES.any { it.equals(type, ignoreCase = true) }
        if (casual && !abbreviated) return@mapNotNull null
        // A spelled-out type in lowercase is usually just a word ("a 9 Coffee run").
        if (!abbreviated && type.first().isLowerCase()) return@mapNotNull null
        val value = match.value.trim().trimEnd(',', '.', ';').replace(Regex("\\s+"), " ")
        CopyTarget(CopyTarget.Kind.Address, value)
    }.toList()

    private val URL = Regex("""(?:https?://|www\.)\S+""", RegexOption.IGNORE_CASE)
}
