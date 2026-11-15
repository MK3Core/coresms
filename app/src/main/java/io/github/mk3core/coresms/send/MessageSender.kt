package io.github.mk3core.coresms.send

import android.content.Context
import android.net.Uri
import io.github.mk3core.coresms.data.Message
import io.github.mk3core.coresms.data.Messages
import io.github.mk3core.coresms.mms.CarrierLimits
import io.github.mk3core.coresms.mms.MediaPrep
import io.github.mk3core.coresms.mms.MmsSender
import io.github.mk3core.coresms.mms.PreparedMedia
import io.github.mk3core.coresms.sms.SmsManagers
import io.github.mk3core.coresms.sms.SmsSender

/**
 * The one way CoreSMS sends: plain SMS for text to one person, otherwise a
 * single MMS. A group is never split into separate texts.
 */
object MessageSender {

    /** Must be called off the main thread (photos are resized here). */
    fun send(
        context: Context,
        recipients: List<String>,
        text: String,
        attachments: List<Uri> = emptyList(),
        subId: Int = SmsManagers.defaultSubscription(),
    ) {
        require(recipients.isNotEmpty()) { "No recipients" }
        if (recipients.size == 1 && attachments.isEmpty()) {
            SmsSender.send(context, recipients.single(), text, subId)
            return
        }
        val media = MediaPrep.prepare(context, attachments, CarrierLimits.forSubscription(context, subId))
        MmsSender.send(context, recipients, text, media, subId)
    }

    /** Sends a failed message again. Must be called off the main thread. */
    fun retry(context: Context, message: Message, threadRecipients: List<String>) {
        if (!message.isMms) {
            Messages.delete(context, message)
            send(context, threadRecipients, message.body, subId = message.subId)
            return
        }
        // Read the recipients and attachments back before deleting the failed copy.
        val recipients = Messages.mmsRecipients(context, message.id).ifEmpty { threadRecipients }
        val media = message.attachments.mapIndexedNotNull { index, attachment ->
            val bytes = context.contentResolver.openInputStream(attachment.uri)?.use { it.readBytes() }
                ?: return@mapIndexedNotNull null
            PreparedMedia(bytes, attachment.mimeType, attachment.name ?: "attachment_$index")
        }
        Messages.delete(context, message)
        MmsSender.send(context, recipients, message.body, media, message.subId)
    }
}
