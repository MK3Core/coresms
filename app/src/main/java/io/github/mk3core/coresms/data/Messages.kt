package io.github.mk3core.coresms.data

import android.content.Context
import android.net.Uri
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import io.github.mk3core.coresms.mms.MmsDownloadWorker
import io.github.mk3core.coresms.mms.MmsStore
import io.github.mk3core.coresms.mmslib.pdu.EncodedStringValue
import io.github.mk3core.coresms.mmslib.pdu.PduHeaders
import io.github.mk3core.coresms.reactions.Reaction

data class Attachment(val uri: Uri, val mimeType: String, val name: String?) {
    val isImage get() = mimeType.startsWith("image/")
    val isVideo get() = mimeType.startsWith("video/")
    val isAudio get() = mimeType.startsWith("audio/")
    val isContactCard get() = mimeType == "text/x-vcard" || mimeType == "text/vcard"
}

enum class MessageStatus { Received, Sending, Sent, Failed }

enum class DownloadState {
    /** Queued, waiting for a connection or the next retry. */
    Waiting,
    Downloading,
    /** Tried and failed; tap to try again. */
    Failed,
    /** Never tried by CoreSMS (e.g. another app gave up on it); tap to download. */
    NotDownloaded,
    /** Past the carrier's expiry; tapping still tries, in case the carrier kept it. */
    Expired,
}

/** An MMS the carrier has told us about but we haven't downloaded yet. */
data class PendingDownload(
    val state: DownloadState,
    val sizeBytes: Long,
    val expiresAtMillis: Long?,
    val error: String?,
) {
    /** Tapping asks the carrier again, unless a download is already under way. */
    val canRetry get() = state != DownloadState.Waiting && state != DownloadState.Downloading
}

data class Message(
    val id: Long,
    val isMms: Boolean,
    val threadId: Long,
    val date: Long,
    val incoming: Boolean,
    /** Who sent it, for incoming messages. */
    val sender: String?,
    val body: String,
    val attachments: List<Attachment>,
    val status: MessageStatus,
    val subId: Int,
    val pending: PendingDownload? = null,
    val error: String? = null,
    val reactions: List<Reaction> = emptyList(),
) {
    val key: String get() = (if (isMms) "m" else "s") + id
}

/** Reads the messages of one conversation, SMS and MMS merged in time order. */
object Messages {

    fun load(context: Context, threadId: Long, limit: Int = 400): List<Message> = loadPage(context, threadId, limit).messages

    class Page(val messages: List<Message>, val hasOlder: Boolean)

    /** The newest [limit] messages, and whether there are older ones to load. */
    fun loadPage(context: Context, threadId: Long, limit: Int): Page {
        val sms = loadSms(context, threadId, limit)
        val mms = loadMms(context, threadId, limit)
        val all = (sms + mms).sortedBy { it.date }
        val hasOlder = all.size > limit || sms.size >= limit || mms.size >= limit
        return Page(all.takeLast(limit), hasOlder)
    }

    /** Unread incoming messages in a thread, oldest first, for notifications. */
    fun unread(context: Context, threadId: Long): List<Message> {
        val unreadSms = unreadIds(context, threadId, mms = false)
        val unreadMms = unreadIds(context, threadId, mms = true)
        if (unreadSms.isEmpty() && unreadMms.isEmpty()) return emptyList()
        return load(context, threadId, limit = 50).filter {
            it.incoming && it.id in (if (it.isMms) unreadMms else unreadSms)
        }
    }

    private fun unreadIds(context: Context, threadId: Long, mms: Boolean): Set<Long> {
        val ids = HashSet<Long>()
        val uri = if (mms) Mms.CONTENT_URI else Sms.CONTENT_URI
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf("_id"),
                "thread_id = ? AND read = 0",
                arrayOf(threadId.toString()),
                null,
            )?.use { while (it.moveToNext()) ids += it.getLong(0) }
        }
        return ids
    }

    private fun loadSms(context: Context, threadId: Long, limit: Int): List<Message> {
        val result = mutableListOf<Message>()
        runCatching {
            context.contentResolver.query(
                Sms.CONTENT_URI,
                arrayOf(Sms._ID, Sms.ADDRESS, Sms.BODY, Sms.DATE, Sms.TYPE, Sms.SUBSCRIPTION_ID),
                "${Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Sms.DATE} DESC",
            )?.use { cursor ->
                while (cursor.moveToNext() && result.size < limit) {
                    val type = cursor.getInt(4)
                    val incoming = type == Sms.MESSAGE_TYPE_INBOX
                    result += Message(
                        id = cursor.getLong(0),
                        isMms = false,
                        threadId = threadId,
                        date = cursor.getLong(3),
                        incoming = incoming,
                        sender = if (incoming) cursor.getString(1) else null,
                        body = cursor.getString(2).orEmpty(),
                        attachments = emptyList(),
                        status = when (type) {
                            Sms.MESSAGE_TYPE_INBOX -> MessageStatus.Received
                            Sms.MESSAGE_TYPE_FAILED -> MessageStatus.Failed
                            Sms.MESSAGE_TYPE_OUTBOX, Sms.MESSAGE_TYPE_QUEUED -> MessageStatus.Sending
                            else -> MessageStatus.Sent
                        },
                        subId = cursor.getInt(5),
                    )
                }
            }
        }
        return result
    }

    private fun loadMms(context: Context, threadId: Long, limit: Int): List<Message> {
        data class Row(
            val id: Long, val date: Long, val box: Int, val type: Int, val status: Int,
            val size: Long, val expiry: Long, val subId: Int,
        )
        val rows = mutableListOf<Row>()
        runCatching {
            context.contentResolver.query(
                Mms.CONTENT_URI,
                arrayOf(Mms._ID, Mms.DATE, Mms.MESSAGE_BOX, Mms.MESSAGE_TYPE, Mms.STATUS, Mms.MESSAGE_SIZE, Mms.EXPIRY, Mms.SUBSCRIPTION_ID),
                "${Mms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Mms.DATE} DESC",
            )?.use { cursor ->
                while (cursor.moveToNext() && rows.size < limit) {
                    rows += Row(
                        cursor.getLong(0), cursor.getLong(1) * 1000, cursor.getInt(2), cursor.getInt(3),
                        cursor.getInt(4), cursor.getLong(5), cursor.getLong(6), cursor.getInt(7),
                    )
                }
            }
        }
        if (rows.isEmpty()) return emptyList()

        val parts = loadParts(context, rows.map { it.id })
        return rows.map { row ->
            val incoming = row.box == Mms.MESSAGE_BOX_INBOX
            val messageParts = parts[row.id].orEmpty()
            val pending = if (row.type == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) {
                val expiresAt = row.expiry.takeIf { it > 0 }?.times(1000)
                val expired = expiresAt != null && expiresAt < System.currentTimeMillis()
                val queued = MmsDownloadWorker.isQueued(context, row.id)
                val error = Prefs.mmsError(context, row.id)
                PendingDownload(
                    state = when {
                        queued && row.status == MmsStore.ST_DOWNLOADING -> DownloadState.Downloading
                        queued -> DownloadState.Waiting
                        expired -> DownloadState.Expired
                        error != null -> DownloadState.Failed
                        else -> DownloadState.NotDownloaded
                    },
                    sizeBytes = row.size,
                    expiresAtMillis = expiresAt,
                    error = error,
                )
            } else {
                null
            }
            Message(
                id = row.id,
                isMms = true,
                threadId = threadId,
                date = row.date,
                incoming = incoming,
                sender = if (incoming) sender(context, row.id) else null,
                body = messageParts.filter { it.text != null }.joinToString("\n") { it.text!! }.trim(),
                attachments = messageParts.mapNotNull { it.attachment },
                status = when (row.box) {
                    Mms.MESSAGE_BOX_INBOX -> MessageStatus.Received
                    Mms.MESSAGE_BOX_FAILED -> MessageStatus.Failed
                    Mms.MESSAGE_BOX_OUTBOX -> MessageStatus.Sending
                    else -> MessageStatus.Sent
                },
                subId = row.subId,
                pending = pending,
                error = if (row.box == Mms.MESSAGE_BOX_FAILED) Prefs.mmsError(context, row.id) else null,
            )
        }
    }

    private class Part(val text: String?, val attachment: Attachment?)

    private fun loadParts(context: Context, ids: List<Long>): Map<Long, List<Part>> {
        val result = HashMap<Long, MutableList<Part>>()
        ids.chunked(200).forEach { chunk ->
            runCatching {
                context.contentResolver.query(
                    Uri.parse("content://mms/part"),
                    arrayOf(Mms.Part._ID, Mms.Part.MSG_ID, Mms.Part.CONTENT_TYPE, Mms.Part.TEXT, Mms.Part.NAME, Mms.Part.FILENAME, Mms.Part.CHARSET),
                    "${Mms.Part.MSG_ID} IN (${chunk.joinToString(",")})",
                    null,
                    "${Mms.Part.SEQ}, ${Mms.Part._ID}",
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val partId = cursor.getLong(0)
                        val messageId = cursor.getLong(1)
                        val type = cursor.getString(2)?.lowercase() ?: continue
                        val part = when {
                            type == "application/smil" -> null
                            type == "text/plain" -> Part(cursor.getString(3).orEmpty(), null)
                            else -> Part(
                                null,
                                Attachment(
                                    Uri.parse("content://mms/part/$partId"),
                                    if (type == "image/jpg") "image/jpeg" else type,
                                    cursor.getString(5) ?: cursor.getString(4),
                                ),
                            )
                        }
                        if (part != null) result.getOrPut(messageId) { mutableListOf() } += part
                    }
                }
            }
        }
        return result
    }

    // Senders never change once a message is stored, so they're cached: big
    // group threads would otherwise need one lookup per message on every load.
    private val senderCache = android.util.LruCache<Long, String>(4000)
    private const val NO_SENDER = "\u0000"

    private fun sender(context: Context, messageId: Long): String? {
        senderCache.get(messageId)?.let { return it.takeIf { it != NO_SENDER } }
        val sender = querySender(context, messageId)
        senderCache.put(messageId, sender ?: NO_SENDER)
        return sender
    }

    private fun querySender(context: Context, messageId: Long): String? = runCatching {
        context.contentResolver.query(
            Uri.parse("content://mms/$messageId/addr"),
            arrayOf(Mms.Addr.ADDRESS, Mms.Addr.CHARSET),
            "${Mms.Addr.TYPE} = ${PduHeaders.FROM}",
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val raw = cursor.getString(0) ?: return@use null
            val charset = cursor.getInt(1)
            if (charset != 0) {
                runCatching { EncodedStringValue(charset, raw.toByteArray(Charsets.ISO_8859_1)).string }.getOrDefault(raw)
            } else {
                raw
            }
        }
    }.getOrNull()

    /** The recipients of an MMS we sent (for resending). */
    fun mmsRecipients(context: Context, messageId: Long): List<String> {
        val result = mutableListOf<String>()
        runCatching {
            context.contentResolver.query(
                Uri.parse("content://mms/$messageId/addr"),
                arrayOf(Mms.Addr.ADDRESS),
                "${Mms.Addr.TYPE} = ${PduHeaders.TO}",
                null,
                null,
            )?.use { while (it.moveToNext()) it.getString(0)?.let(result::add) }
        }
        return result
    }

    fun delete(context: Context, message: Message) {
        val uri = if (message.isMms) MmsStore.messageUri(message.id) else Uri.withAppendedPath(Sms.CONTENT_URI, message.id.toString())
        runCatching { context.contentResolver.delete(uri, null, null) }
    }
}
