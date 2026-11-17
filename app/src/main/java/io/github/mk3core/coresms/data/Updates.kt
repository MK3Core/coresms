package io.github.mk3core.coresms.data

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Sorts conversations into Conversations (people) and Updates (codes,
 * alerts, automated texts), so automated messages stay out of the way.
 *
 * A choice you make by moving a conversation always wins and is remembered
 * for its sender, so their future texts land in the same place. Otherwise a
 * conversation is an update when it's from:
 *
 * - a short code ("72975") or a named sender ("GOOGLE"), or
 * - a toll-free number that isn't a saved contact, or
 * - a number that isn't a saved contact, that you've never texted, whose
 *   messages include a verification code (the "Copy 123456" chip).
 *
 * Saved contacts and group conversations are never moved automatically.
 */
object Updates {

    /** True if [conversation] belongs in Updates. Does a little disk work for unknown numbers; call off the main thread. */
    fun isUpdate(context: Context, conversation: Conversation): Boolean {
        val people = conversation.people
        if (people.isEmpty()) return false
        Prefs.inboxChoice(context, key(people))?.let { return it }
        if (people.size > 1) return false
        val person = people[0]
        if (person.name != null) return false
        if (isAutomatedSender(person.address) || isTollFree(person.address)) return true
        return looksLikeCodeSender(context, conversation.threadId, conversation.date)
    }

    /** Remembers that this conversation's sender belongs in Updates (or not). */
    fun move(context: Context, conversation: Conversation, toUpdates: Boolean) {
        Prefs.setInboxChoice(context, key(conversation.people), toUpdates)
        codeSenders.remove(conversation.threadId)
    }

    /** The same people give the same key, whatever the thread or number format. */
    fun key(people: List<Contact>): String = people.map { addressKey(it.address) }.sorted().joinToString(",")

    /** Short codes ("72975") and named senders ("GOOGLE", "Chase"). Not emails, which can be people. */
    fun isAutomatedSender(address: String): Boolean {
        val a = address.trim()
        if (a.isEmpty() || a.contains('@')) return false
        if (a.any { it.isLetter() }) return true
        val digits = a.count { it.isDigit() }
        return digits in 3..6 && a.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }
    }

    /** North American toll-free numbers, which are almost always businesses. */
    fun isTollFree(address: String): Boolean {
        val digits = address.filter { it.isDigit() }
        val national = when {
            digits.length == 11 && digits.startsWith("1") -> digits.drop(1)
            digits.length == 10 -> digits
            else -> return false
        }
        return national.take(3) in TOLL_FREE
    }

    /** Last 10 digits for phone numbers (so +1 and formatting don't matter), lowercase for everything else. */
    internal fun addressKey(address: String): String {
        val a = address.trim()
        if (a.contains('@') || a.any { it.isLetter() }) return a.lowercase()
        val digits = a.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    /**
     * An unknown number you've never written to, sending a verification code.
     * Remembered per thread until a newer message arrives, so the list
     * doesn't reread messages on every refresh.
     */
    private fun looksLikeCodeSender(context: Context, threadId: Long, date: Long): Boolean {
        codeSenders[threadId]?.let { (checkedAt, result) -> if (checkedAt == date) return result }
        val recent = Messages.load(context, threadId, limit = RECENT)
        val result = recent.none { !it.incoming } &&
            recent.any { message -> QuickCopy.find(message.body).any { it.kind == CopyTarget.Kind.Code } }
        codeSenders[threadId] = date to result
        return result
    }

    private val codeSenders = ConcurrentHashMap<Long, Pair<Long, Boolean>>()
    private const val RECENT = 10
    private val TOLL_FREE = setOf("800", "833", "844", "855", "866", "877", "888")
}
