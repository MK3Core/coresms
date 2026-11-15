package io.github.mk3core.coresms.reactions

/** A reaction parsed from a text message. */
data class ParsedReaction(
    val emoji: String,
    /** The quoted text of the message reacted to, or null for "an image" style reactions. */
    val quoted: String?,
    /** What was reacted to when there's no quote: "image", "video", etc. */
    val target: String? = null,
    val removed: Boolean = false,
)

/** A reaction attached to a message in a thread. */
data class Reaction(val emoji: String, val sender: String?, val fromMe: Boolean)

/**
 * Over SMS/MMS, iPhones and Google Messages send reactions as ordinary
 * texts like `Loved “See you at 6”` or `Reacted 🔥 to “See you at 6”`.
 * This turns them back into reactions, and builds outgoing ones in the form
 * iPhones turn into real tapbacks.
 */
object Reactions {

    // The six classic iPhone tapbacks, and how each is described when removed.
    private val TAPBACKS = listOf(
        Tapback("Loved", "❤️", "a heart"),
        Tapback("Liked", "👍", "a like"),
        Tapback("Disliked", "👎", "a dislike"),
        Tapback("Laughed at", "😂", "a laugh"),
        Tapback("Emphasized", "‼️", "an exclamation"),
        Tapback("Questioned", "❓", "a question mark"),
    )

    private data class Tapback(val verb: String, val emoji: String, val removedName: String)

    /** Quick-pick reactions shown when long-pressing a message. */
    val QUICK = TAPBACKS.map { it.emoji }

    private const val QUOTE = "[“\"](.+)[”\"]"
    private val verbs = TAPBACKS.joinToString("|") { Regex.escape(it.verb) }
    private val removedNames = TAPBACKS.joinToString("|") { Regex.escape(it.removedName) }

    private val CLASSIC = Regex("^($verbs) $QUOTE$", RegexOption.DOT_MATCHES_ALL)
    private val CLASSIC_MEDIA = Regex("^($verbs) an? (image|photo|video|movie|audio message|attachment)$")
    private val EMOJI = Regex("^Reacted (.+?) to $QUOTE$", RegexOption.DOT_MATCHES_ALL)
    private val EMOJI_MEDIA = Regex("^Reacted (.+?) to an? (image|photo|video|movie|audio message|attachment)$")
    private val REMOVED_CLASSIC = Regex("^Removed ($removedNames) from $QUOTE$", RegexOption.DOT_MATCHES_ALL)
    private val REMOVED_EMOJI = Regex("^Removed (.+?) from $QUOTE$", RegexOption.DOT_MATCHES_ALL)
    // Google Messages to non-RCS phones: `👍 to “…”`
    private val GOOGLE = Regex("^(\\S{1,8}) to $QUOTE$", RegexOption.DOT_MATCHES_ALL)

    fun parse(body: String): ParsedReaction? {
        val text = body.trim()
        if (text.length < 6) return null
        CLASSIC.matchEntire(text)?.let { m ->
            return ParsedReaction(tapback(m.groupValues[1]).emoji, m.groupValues[2])
        }
        CLASSIC_MEDIA.matchEntire(text)?.let { m ->
            return ParsedReaction(tapback(m.groupValues[1]).emoji, null, target = m.groupValues[2])
        }
        REMOVED_CLASSIC.matchEntire(text)?.let { m ->
            val tapback = TAPBACKS.first { it.removedName == m.groupValues[1] }
            return ParsedReaction(tapback.emoji, m.groupValues[2], removed = true)
        }
        EMOJI.matchEntire(text)?.let { m ->
            if (isEmoji(m.groupValues[1])) return ParsedReaction(m.groupValues[1], m.groupValues[2])
        }
        EMOJI_MEDIA.matchEntire(text)?.let { m ->
            if (isEmoji(m.groupValues[1])) return ParsedReaction(m.groupValues[1], null, target = m.groupValues[2])
        }
        REMOVED_EMOJI.matchEntire(text)?.let { m ->
            if (isEmoji(m.groupValues[1])) return ParsedReaction(m.groupValues[1], m.groupValues[2], removed = true)
        }
        GOOGLE.matchEntire(text)?.let { m ->
            if (isEmoji(m.groupValues[1])) return ParsedReaction(m.groupValues[1], m.groupValues[2])
        }
        return null
    }

    /** The text to send for reacting to [targetBody] with [emoji], in the form iPhones understand. */
    fun compose(emoji: String, targetBody: String, targetHasMedia: Boolean, removed: Boolean = false): String {
        val tapback = TAPBACKS.firstOrNull { it.emoji == emoji }
        if (targetBody.isBlank()) {
            val what = if (targetHasMedia) "an image" else "a message"
            return when {
                removed -> "Removed $emoji from $what"
                tapback != null -> "${tapback.verb} $what"
                else -> "Reacted $emoji to $what"
            }
        }
        val quoted = "“$targetBody”"
        return when {
            removed && tapback != null -> "Removed ${tapback.removedName} from $quoted"
            removed -> "Removed $emoji from $quoted"
            tapback != null -> "${tapback.verb} $quoted"
            else -> "Reacted $emoji to $quoted"
        }
    }

    /** True if [body] is the message [quoted] refers to (iPhones shorten long quotes with "…"). */
    fun matches(quoted: String, body: String): Boolean {
        val q = quoted.trim()
        val b = body.trim()
        if (q == b) return true
        if (q.endsWith("…") || q.endsWith("...")) {
            val prefix = q.removeSuffix("…").removeSuffix("...").trimEnd()
            return prefix.isNotEmpty() && b.startsWith(prefix)
        }
        return false
    }

    /** A short description for previews, e.g. `❤️ to “See you at 6”`. */
    fun describe(reaction: ParsedReaction): String {
        val target = reaction.quoted?.let { "“$it”" } ?: "an ${reaction.target ?: "attachment"}"
        return if (reaction.removed) "Removed ${reaction.emoji} from $target" else "${reaction.emoji} to $target"
    }

    private fun tapback(verb: String) = TAPBACKS.first { it.verb == verb }

    /** Rough check that a string is an emoji (not a word), so normal sentences aren't mistaken for reactions. */
    private fun isEmoji(text: String): Boolean =
        text.isNotEmpty() && text.length <= 16 && text.none { it.isLetterOrDigit() } &&
            text.codePoints().anyMatch { it >= 0x2000 }
}
