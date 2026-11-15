package io.github.mk3core.coresms.reactions

import io.github.mk3core.coresms.data.Message
import io.github.mk3core.coresms.data.Phone

/**
 * Turns reaction texts in a thread into reactions on the messages they
 * target, and hides the reaction texts themselves. A reaction whose target
 * can't be found stays as a normal message, so nothing is hidden by mistake.
 */
object ReactionFolding {

    private const val SEARCH_DEPTH = 300

    fun fold(messages: List<Message>): List<Message> {
        val visible = ArrayList<Message>(messages.size)
        val reactions = HashMap<String, MutableList<Reaction>>()

        for (message in messages) {
            val parsed = if (message.attachments.isEmpty() && message.pending == null) {
                Reactions.parse(message.body)
            } else {
                null
            }
            val target = parsed?.let { findTarget(visible, it, message) }
            if (parsed == null || target == null) {
                visible += message
                continue
            }
            val reaction = Reaction(parsed.emoji, message.sender, fromMe = !message.incoming)
            val list = reactions.getOrPut(target.key) { mutableListOf() }
            // One reaction per person per message, like iMessage.
            list.removeAll { sameReactor(it, reaction) && (!parsed.removed || it.emoji == parsed.emoji) }
            if (!parsed.removed) list += reaction
        }
        return visible.map { message ->
            reactions[message.key]?.let { message.copy(reactions = it.toList()) } ?: message
        }
    }

    private fun findTarget(visible: List<Message>, parsed: ParsedReaction, reactor: Message): Message? {
        val candidates = visible.asReversed().asSequence().take(SEARCH_DEPTH)
        val quoted = parsed.quoted
        val matching = if (quoted != null) {
            candidates.filter { Reactions.matches(quoted, it.body) }
        } else {
            val wantVideo = parsed.target == "video" || parsed.target == "movie"
            candidates.filter { candidate ->
                candidate.attachments.any { if (wantVideo) it.isVideo else it.isImage || it.isVideo }
            }
        }
        // People rarely react to their own messages, so prefer someone else's.
        return matching.firstOrNull { !sameAuthor(it, reactor) } ?: matching.firstOrNull()
    }

    private fun sameAuthor(a: Message, b: Message): Boolean = when {
        !a.incoming && !b.incoming -> true
        a.incoming && b.incoming -> a.sender != null && b.sender != null && Phone.same(a.sender, b.sender)
        else -> false
    }

    private fun sameReactor(a: Reaction, b: Reaction): Boolean = when {
        a.fromMe || b.fromMe -> a.fromMe && b.fromMe
        else -> a.sender != null && b.sender != null && Phone.same(a.sender, b.sender)
    }
}
