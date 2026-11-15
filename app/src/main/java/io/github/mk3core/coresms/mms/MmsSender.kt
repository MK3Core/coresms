package io.github.mk3core.coresms.mms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.mk3core.coresms.mmslib.pdu.CharacterSets
import io.github.mk3core.coresms.mmslib.pdu.EncodedStringValue
import io.github.mk3core.coresms.mmslib.pdu.PduBody
import io.github.mk3core.coresms.mmslib.pdu.PduComposer
import io.github.mk3core.coresms.mmslib.pdu.PduHeaders
import io.github.mk3core.coresms.mmslib.pdu.PduPart
import io.github.mk3core.coresms.mmslib.pdu.SendReq
import io.github.mk3core.coresms.sms.SmsManagers

/**
 * Sends one MMS to everyone in [recipients] at once (all in To), which is
 * what makes a group conversation: every recipient sees the others and
 * replies go to the whole group.
 */
object MmsSender {

    private const val EXPIRY_SECONDS = 7 * 24 * 60 * 60L

    /** Saves the message to the outbox and hands it to the carrier. Returns the message id. */
    fun send(
        context: Context,
        recipients: List<String>,
        text: String,
        media: List<PreparedMedia>,
        subId: Int,
    ): Long {
        val req = buildSendReq(recipients, text, media)
        val id = MmsStore.persistOutgoing(context, req, recipients, subId)
            ?: throw IllegalStateException("Couldn't save the outgoing MMS")
        transmit(context, id, req, subId)
        return id
    }

    private fun transmit(context: Context, id: Long, req: SendReq, subId: Int) {
        val pdu = PduComposer(context, req).make() ?: throw IllegalStateException("Couldn't build the MMS")
        val file = MmsTransport.newFile(context, "send")
        file.writeBytes(pdu)
        val sent = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            Intent(context, MmsSentReceiver::class.java)
                .setData(MmsStore.messageUri(id))
                .putExtra(MmsSentReceiver.EXTRA_FILE, file.absolutePath)
                .putExtra(MmsSentReceiver.EXTRA_SUB_ID, subId),
            // Mutable so the MMS service can add the HTTP status and response.
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            SmsManagers.forSubscription(context, subId)
                .sendMultimediaMessage(context, MmsTransport.shareUri(context, file), null, null, sent)
        } catch (e: Exception) {
            file.delete()
            MmsStore.moveToBox(context, id, android.provider.Telephony.Mms.MESSAGE_BOX_FAILED)
            throw e
        }
    }

    fun buildSendReq(recipients: List<String>, text: String, media: List<PreparedMedia>): SendReq {
        val body = PduBody()
        val smilEntries = mutableListOf<String>()
        var size = 0L

        media.forEach { item ->
            val location = item.fileName
            body.addPart(
                PduPart().apply {
                    contentType = item.mimeType.toByteArray()
                    contentLocation = location.toByteArray()
                    contentId = "<${location.substringBeforeLast('.')}>".toByteArray()
                    name = location.toByteArray()
                    filename = location.toByteArray()
                    data = item.bytes
                },
            )
            size += item.bytes.size
            val tag = when {
                item.mimeType.startsWith("image/") -> "img"
                item.mimeType.startsWith("video/") -> "video"
                item.mimeType.startsWith("audio/") -> "audio"
                else -> "ref"
            }
            smilEntries += """<par dur="5000ms"><$tag src="$location" region="Image"/></par>"""
        }

        if (text.isNotEmpty()) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            body.addPart(
                PduPart().apply {
                    charset = CharacterSets.UTF_8
                    contentType = "text/plain".toByteArray()
                    contentLocation = "text_0.txt".toByteArray()
                    contentId = "<text_0>".toByteArray()
                    data = bytes
                },
            )
            size += bytes.size
            smilEntries += """<par dur="5000ms"><text src="text_0.txt" region="Text"/></par>"""
        }

        val smil = buildString {
            append("<smil><head><layout><root-layout/>")
            append("""<region id="Image" fit="meet" top="0" left="0" height="80%" width="100%"/>""")
            append("""<region id="Text" top="80%" left="0" height="20%" width="100%"/>""")
            append("</layout></head><body>")
            smilEntries.forEach { append(it) }
            append("</body></smil>")
        }.toByteArray()
        body.addPart(
            0,
            PduPart().apply {
                contentType = "application/smil".toByteArray()
                contentLocation = "smil.xml".toByteArray()
                contentId = "<smil>".toByteArray()
                data = smil
            },
        )
        size += smil.size

        return SendReq().apply {
            from = EncodedStringValue(PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR)
            to = recipients.map { EncodedStringValue(it) }.toTypedArray()
            date = System.currentTimeMillis() / 1000
            transactionId = "T${java.lang.Long.toHexString(System.currentTimeMillis())}".toByteArray()
            messageClass = PduHeaders.MESSAGE_CLASS_PERSONAL_STR.toByteArray()
            expiry = EXPIRY_SECONDS
            priority = PduHeaders.PRIORITY_NORMAL
            deliveryReport = PduHeaders.VALUE_NO
            readReport = PduHeaders.VALUE_NO
            contentType = "application/vnd.wap.multipart.related".toByteArray()
            messageSize = size
            this.body = body
        }
    }
}
