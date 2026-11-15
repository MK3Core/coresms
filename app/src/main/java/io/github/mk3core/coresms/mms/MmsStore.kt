package io.github.mk3core.coresms.mms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.provider.Telephony.Mms
import io.github.mk3core.coresms.data.Phone
import io.github.mk3core.coresms.data.SelfNumbers
import io.github.mk3core.coresms.mmslib.pdu.CharacterSets
import io.github.mk3core.coresms.mmslib.pdu.EncodedStringValue
import io.github.mk3core.coresms.mmslib.pdu.MultimediaMessagePdu
import io.github.mk3core.coresms.mmslib.pdu.NotificationInd
import io.github.mk3core.coresms.mmslib.pdu.PduHeaders
import io.github.mk3core.coresms.mmslib.pdu.PduPart
import io.github.mk3core.coresms.mmslib.pdu.RetrieveConf
import io.github.mk3core.coresms.mmslib.pdu.SendReq

/**
 * Reads and writes MMS in Android's message database (content://mms), using
 * the same columns as AOSP's PduPersister so other SMS apps see them too.
 */
object MmsStore {

    // Download states for not-yet-downloaded MMS ("st" column), as used by AOSP's Mms app.
    const val ST_UNSTARTED = 0x80
    const val ST_DOWNLOADING = 0x81
    const val ST_TRANSIENT_FAILURE = 0x82
    const val ST_PERMANENT_FAILURE = 0x87

    private const val ADDR_FROM = PduHeaders.FROM // 137
    private const val ADDR_TO = PduHeaders.TO // 151
    private const val ADDR_CC = PduHeaders.CC // 130
    private const val ADDR_BCC = PduHeaders.BCC // 129

    fun iso(bytes: ByteArray?): String? = bytes?.let { String(it, Charsets.ISO_8859_1) }

    fun messageUri(id: Long): Uri = ContentUris.withAppendedId(Mms.CONTENT_URI, id)

    /** An existing message (downloaded or not) with this MMS transaction id, if any. */
    fun findByTransactionId(context: Context, transactionId: String): Long? =
        runCatching {
            context.contentResolver.query(
                Mms.CONTENT_URI,
                arrayOf(Mms._ID),
                "${Mms.TRANSACTION_ID} = ?",
                arrayOf(transactionId),
                null,
            )?.use { if (it.moveToFirst()) it.getLong(0) else null }
        }.getOrNull()

    /**
     * Saves an MMS notification ("a message is waiting at the carrier") as a
     * not-yet-downloaded message in the sender's thread. Returns its id.
     */
    fun persistNotification(context: Context, ind: NotificationInd, subId: Int): Long? {
        val from = ind.from?.string?.takeIf { it.isNotBlank() } ?: UNKNOWN_SENDER
        val threadId = Telephony.Threads.getOrCreateThreadId(context, from)
        val now = System.currentTimeMillis() / 1000
        val values = ContentValues().apply {
            put(Mms.THREAD_ID, threadId)
            put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_INBOX)
            put(Mms.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND)
            put(Mms.DATE, now)
            put(Mms.READ, 0)
            put(Mms.SEEN, 0)
            put(Mms.STATUS, ST_UNSTARTED)
            put(Mms.SUBSCRIPTION_ID, subId)
            iso(ind.contentLocation)?.let { put(Mms.CONTENT_LOCATION, it) }
            iso(ind.transactionId)?.let { put(Mms.TRANSACTION_ID, it) }
            iso(ind.messageClass)?.let { put(Mms.MESSAGE_CLASS, it) }
            if (ind.expiry > 0) put(Mms.EXPIRY, ind.expiry)
            if (ind.messageSize > 0) put(Mms.MESSAGE_SIZE, ind.messageSize)
            if (ind.mmsVersion != 0) put(Mms.MMS_VERSION, ind.mmsVersion)
            ind.subject?.let {
                put(Mms.SUBJECT, iso(it.textString))
                put(Mms.SUBJECT_CHARSET, it.characterSet)
            }
        }
        val uri = context.contentResolver.insert(Mms.Inbox.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(uri)
        insertAddress(context, id, ADDR_FROM, ind.from ?: EncodedStringValue(from))
        return id
    }

    /**
     * Saves a downloaded MMS in the right thread: everyone in From, To and Cc
     * except us, so group messages land in the group conversation.
     */
    fun persistRetrieved(
        context: Context,
        conf: RetrieveConf,
        subId: Int,
        transactionId: String?,
        contentLocation: String?,
    ): Long? {
        val from = conf.from?.string?.takeIf { it.isNotBlank() } ?: UNKNOWN_SENDER
        val to = conf.to.orEmpty().mapNotNull { it?.string }
        val cc = conf.cc.orEmpty().mapNotNull { it?.string }
        val threadId = Telephony.Threads.getOrCreateThreadId(
            context,
            participants(context, subId, from, to + cc).toSet(),
        )

        val date = conf.date.takeIf { it > 0 } ?: (System.currentTimeMillis() / 1000)
        val values = ContentValues().apply {
            put(Mms.THREAD_ID, threadId)
            put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_INBOX)
            put(Mms.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF)
            // Received time, so messages sort in the order they arrived here.
            put(Mms.DATE, System.currentTimeMillis() / 1000)
            put(Mms.DATE_SENT, date)
            put(Mms.READ, 0)
            put(Mms.SEEN, 0)
            put(Mms.SUBSCRIPTION_ID, subId)
            put(Mms.TEXT_ONLY, if (isTextOnly(conf)) 1 else 0)
            iso(conf.contentType)?.let { put(Mms.CONTENT_TYPE, it) }
            iso(conf.messageId)?.let { put(Mms.MESSAGE_ID, it) }
            iso(conf.messageClass)?.let { put(Mms.MESSAGE_CLASS, it) }
            transactionId?.let { put(Mms.TRANSACTION_ID, it) }
            contentLocation?.let { put(Mms.CONTENT_LOCATION, it) }
            if (conf.mmsVersion != 0) put(Mms.MMS_VERSION, conf.mmsVersion)
            if (conf.priority != 0) put(Mms.PRIORITY, conf.priority)
            conf.subject?.let {
                put(Mms.SUBJECT, iso(it.textString))
                put(Mms.SUBJECT_CHARSET, it.characterSet)
            }
        }
        val uri = context.contentResolver.insert(Mms.Inbox.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(uri)
        persistParts(context, id, conf)
        insertAddress(context, id, ADDR_FROM, conf.from ?: EncodedStringValue(from))
        conf.to?.forEach { it?.let { insertAddress(context, id, ADDR_TO, it) } }
        conf.cc?.forEach { it?.let { insertAddress(context, id, ADDR_CC, it) } }
        return id
    }

    /** Saves an outgoing MMS in the outbox before it's sent. Returns its id. */
    fun persistOutgoing(context: Context, req: SendReq, recipients: List<String>, subId: Int): Long? {
        val threadId = Telephony.Threads.getOrCreateThreadId(context, recipients.toSet())
        val values = ContentValues().apply {
            put(Mms.THREAD_ID, threadId)
            put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_OUTBOX)
            put(Mms.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_SEND_REQ)
            put(Mms.DATE, System.currentTimeMillis() / 1000)
            put(Mms.READ, 1)
            put(Mms.SEEN, 1)
            put(Mms.SUBSCRIPTION_ID, subId)
            put(Mms.TEXT_ONLY, if (isTextOnly(req)) 1 else 0)
            iso(req.contentType)?.let { put(Mms.CONTENT_TYPE, it) }
            iso(req.messageClass)?.let { put(Mms.MESSAGE_CLASS, it) }
            put(Mms.MMS_VERSION, req.mmsVersion)
            put(Mms.MESSAGE_SIZE, req.messageSize)
        }
        val uri = context.contentResolver.insert(Mms.Outbox.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(uri)
        persistParts(context, id, req)
        recipients.forEach { insertAddress(context, id, ADDR_TO, EncodedStringValue(it)) }
        insertAddress(context, id, ADDR_FROM, EncodedStringValue(PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR))
        return id
    }

    /** Everyone in the conversation except us. */
    fun participants(context: Context, subId: Int, from: String, toAndCc: List<String>): List<String> {
        val self = buildList {
            SelfNumbers.forSubscription(context, subId)?.let { add(it) }
            addAll(SelfNumbers.all(context))
        }
        // Same rule as AOSP: if we don't know our own number and only one
        // other address is listed, that address is us.
        val others = if (self.isEmpty() && toAndCc.size == 1) emptyList() else toAndCc
        val result = Phone.others(listOf(from) + others, self)
        return result.ifEmpty { listOf(from) }
    }

    fun setDownloadState(context: Context, id: Long, state: Int) {
        val values = ContentValues().apply { put(Mms.STATUS, state) }
        runCatching { context.contentResolver.update(messageUri(id), values, null, null) }
    }

    fun moveToBox(context: Context, id: Long, box: Int) {
        val values = ContentValues().apply { put(Mms.MESSAGE_BOX, box) }
        runCatching { context.contentResolver.update(messageUri(id), values, null, null) }
    }

    fun delete(context: Context, id: Long) {
        runCatching { context.contentResolver.delete(messageUri(id), null, null) }
    }

    private fun persistParts(context: Context, messageId: Long, pdu: MultimediaMessagePdu) {
        val body = pdu.body ?: return
        for (i in 0 until body.partsNum) {
            runCatching { persistPart(context, messageId, body.getPart(i)) }
        }
    }

    private fun persistPart(context: Context, messageId: Long, part: PduPart) {
        var contentType = iso(part.contentType)?.lowercase() ?: return
        if (contentType == "image/jpg") contentType = "image/jpeg"
        val data = part.data
        val values = ContentValues().apply {
            put(Mms.Part.CONTENT_TYPE, contentType)
            if (part.charset != 0) put(Mms.Part.CHARSET, part.charset)
            part.filename?.let { put(Mms.Part.FILENAME, String(it)) }
            part.name?.let { put(Mms.Part.NAME, String(it)) }
            iso(part.contentDisposition)?.let { put(Mms.Part.CONTENT_DISPOSITION, it) }
            iso(part.contentId)?.let { put(Mms.Part.CONTENT_ID, it) }
            iso(part.contentLocation)?.let { put(Mms.Part.CONTENT_LOCATION, it) }
            if (contentType == "application/smil") put(Mms.Part.SEQ, -1)
            if (isTextType(contentType)) {
                val charset = if (part.charset != 0) part.charset else CharacterSets.UTF_8
                put(Mms.Part.TEXT, data?.let { EncodedStringValue(charset, it).string }.orEmpty())
            }
        }
        val partUri = context.contentResolver.insert(Uri.parse("content://mms/$messageId/part"), values)
            ?: return
        if (!isTextType(contentType) && data != null) {
            context.contentResolver.openOutputStream(partUri)?.use { it.write(data) }
        }
    }

    private fun insertAddress(context: Context, messageId: Long, type: Int, address: EncodedStringValue) {
        val values = ContentValues().apply {
            put(Mms.Addr.ADDRESS, iso(address.textString))
            put(Mms.Addr.CHARSET, address.characterSet)
            put(Mms.Addr.TYPE, type)
        }
        runCatching { context.contentResolver.insert(Uri.parse("content://mms/$messageId/addr"), values) }
    }

    private fun isTextType(contentType: String) =
        contentType == "text/plain" || contentType == "application/smil" || contentType == "text/html"

    private fun isTextOnly(pdu: MultimediaMessagePdu): Boolean {
        val body = pdu.body ?: return true
        for (i in 0 until body.partsNum) {
            val type = iso(body.getPart(i).contentType)?.lowercase() ?: continue
            if (type != "text/plain" && type != "application/smil") return false
        }
        return true
    }

    private const val UNKNOWN_SENDER = "Unknown"

    // Address field types, re-exported for readers of the addr table.
    const val ADDRESS_TYPE_FROM = ADDR_FROM
    const val ADDRESS_TYPE_TO = ADDR_TO
    const val ADDRESS_TYPE_CC = ADDR_CC
    const val ADDRESS_TYPE_BCC = ADDR_BCC
}
