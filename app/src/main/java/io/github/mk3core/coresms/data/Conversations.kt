package io.github.mk3core.coresms.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import io.github.mk3core.coresms.mms.MmsStore
import io.github.mk3core.coresms.mmslib.pdu.EncodedStringValue
import io.github.mk3core.coresms.mmslib.pdu.PduHeaders
import io.github.mk3core.coresms.reactions.Reactions

data class Conversation(
    val threadId: Long,
    val people: List<Contact>,
    val date: Long,
    val snippet: String,
    val unreadCount: Int,
    val hasPendingMms: Boolean,
    val pinned: Boolean,
) {
    val isGroup: Boolean get() = people.size > 1
    val title: String
        get() = when {
            people.isEmpty() -> "Unknown"
            people.size == 1 -> people[0].displayName
            else -> people.joinToString(", ") { it.name?.substringBefore(' ') ?: it.address }
        }
    val addresses: List<String> get() = people.map { it.address }

    /** Needs attention: shown on the first screen of the conversation list. */
    val needsAttention: Boolean get() = unreadCount > 0 || hasPendingMms
}

/** Reads conversations (threads) from Android's message database. */
object Conversations {

    private val THREADS_URI: Uri = Uri.parse("content://mms-sms/conversations?simple=true")
    private val CANONICAL_ADDRESSES_URI: Uri = Uri.parse("content://mms-sms/canonical-addresses")

    fun load(context: Context): List<Conversation> {
        val addresses = canonicalAddresses(context)
        val unread = unreadCounts(context)
        val pendingMms = threadsWithPendingMms(context)
        val pinned = Prefs.pinnedThreads(context).toSet()
        val result = mutableListOf<Conversation>()
        runCatching {
            context.contentResolver.query(
                THREADS_URI,
                arrayOf(
                    Telephony.Threads._ID,
                    Telephony.Threads.DATE,
                    Telephony.Threads.MESSAGE_COUNT,
                    Telephony.Threads.RECIPIENT_IDS,
                    Telephony.Threads.SNIPPET,
                    Telephony.Threads.SNIPPET_CHARSET,
                    Telephony.Threads.HAS_ATTACHMENT,
                ),
                null,
                null,
                "${Telephony.Threads.DATE} DESC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(0)
                    if (cursor.getInt(2) == 0 && threadId !in pendingMms) continue
                    val people = cursor.getString(3).orEmpty().split(' ')
                        .mapNotNull { it.toLongOrNull()?.let(addresses::get) }
                        .map { ContactsRepo.lookup(context, it) }
                    result += Conversation(
                        threadId = threadId,
                        people = people,
                        date = cursor.getLong(1),
                        snippet = snippet(cursor.getString(4), cursor.getInt(5), cursor.getInt(6) != 0),
                        unreadCount = unread[threadId] ?: 0,
                        hasPendingMms = threadId in pendingMms,
                        pinned = threadId in pinned,
                    )
                }
            }
        }
        return result
    }

    /** Just the people and title of one conversation, without loading the whole list. */
    fun find(context: Context, threadId: Long): Conversation? {
        val people = recipients(context, threadId)
        if (people.isEmpty()) return null
        return Conversation(threadId, people, date = 0, snippet = "", unreadCount = 0, hasPendingMms = false, pinned = false)
    }

    /** The people in a thread, without loading every conversation. */
    fun recipients(context: Context, threadId: Long): List<Contact> {
        val addresses = canonicalAddresses(context)
        return runCatching {
            context.contentResolver.query(
                THREADS_URI,
                arrayOf(Telephony.Threads._ID, Telephony.Threads.RECIPIENT_IDS),
                "${Telephony.Threads._ID} = ?",
                arrayOf(threadId.toString()),
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use emptyList()
                cursor.getString(1).orEmpty().split(' ')
                    .mapNotNull { it.toLongOrNull()?.let(addresses::get) }
                    .map { ContactsRepo.lookup(context, it) }
            }
        }.getOrNull().orEmpty()
    }

    fun markRead(context: Context, threadId: Long) {
        val values = ContentValues().apply {
            put(Sms.READ, 1)
            put(Sms.SEEN, 1)
        }
        val where = "${Sms.THREAD_ID} = ? AND (${Sms.READ} = 0 OR ${Sms.SEEN} = 0)"
        val args = arrayOf(threadId.toString())
        runCatching { context.contentResolver.update(Sms.CONTENT_URI, values, where, args) }
        runCatching { context.contentResolver.update(Mms.CONTENT_URI, values, where, args) }
    }

    /** Marks the newest incoming message unread, which brings the conversation back to the top. */
    fun markUnread(context: Context, threadId: Long) {
        val newest = Messages.load(context, threadId, limit = 30).lastOrNull { it.incoming } ?: return
        val values = ContentValues().apply { put(Sms.READ, 0) }
        val uri = if (newest.isMms) MmsStore.messageUri(newest.id) else Uri.withAppendedPath(Sms.CONTENT_URI, newest.id.toString())
        runCatching { context.contentResolver.update(uri, values, null, null) }
    }

    fun delete(context: Context, threadId: Long) {
        runCatching {
            context.contentResolver.delete(Uri.parse("content://mms-sms/conversations/$threadId"), null, null)
        }
        Prefs.setPinned(context, threadId, false)
    }

    private fun canonicalAddresses(context: Context): Map<Long, String> {
        val map = HashMap<Long, String>()
        runCatching {
            context.contentResolver.query(CANONICAL_ADDRESSES_URI, arrayOf("_id", "address"), null, null, null)
                ?.use { cursor -> while (cursor.moveToNext()) map[cursor.getLong(0)] = cursor.getString(1).orEmpty() }
        }
        return map
    }

    private fun unreadCounts(context: Context): Map<Long, Int> {
        val counts = HashMap<Long, Int>()
        fun count(uri: Uri, where: String) = runCatching {
            context.contentResolver.query(uri, arrayOf(Sms.THREAD_ID), where, null, null)?.use { cursor ->
                while (cursor.moveToNext()) counts.merge(cursor.getLong(0), 1, Int::plus)
            }
        }
        count(Sms.CONTENT_URI, "${Sms.READ} = 0 AND ${Sms.TYPE} = ${Sms.MESSAGE_TYPE_INBOX}")
        count(Mms.CONTENT_URI, "${Mms.READ} = 0 AND ${Mms.MESSAGE_BOX} = ${Mms.MESSAGE_BOX_INBOX}")
        return counts
    }

    private fun threadsWithPendingMms(context: Context): Set<Long> {
        val threads = HashSet<Long>()
        runCatching {
            context.contentResolver.query(
                Mms.CONTENT_URI,
                arrayOf(Mms.THREAD_ID),
                "${Mms.MESSAGE_TYPE} = ${PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND}",
                null,
                null,
            )?.use { cursor -> while (cursor.moveToNext()) threads += cursor.getLong(0) }
        }
        return threads
    }

    private fun snippet(raw: String?, charset: Int, hasAttachment: Boolean): String {
        val text = if (raw != null && charset != 0) {
            runCatching { EncodedStringValue(charset, raw.toByteArray(Charsets.ISO_8859_1)).string }.getOrDefault(raw)
        } else {
            raw.orEmpty()
        }
        Reactions.parse(text)?.let { return Reactions.describe(it) }
        return text.ifBlank { if (hasAttachment) "Attachment" else "" }
    }
}
