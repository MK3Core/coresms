package io.github.mk3core.coresms.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import io.github.mk3core.coresms.notify.Notifications

/**
 * Receives every incoming SMS while CoreSMS is the default SMS app.
 *
 * As the default app we're responsible for storing the message: nothing else
 * writes it to the system SMS database. Storing it there (rather than in our
 * own database) keeps history intact if you switch back to another app.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        // A long text arrives as several parts in one broadcast.
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val first = parts?.firstOrNull() ?: return
        val address = first.displayOriginatingAddress ?: first.originatingAddress ?: return
        val body = parts.joinToString("") { it.displayMessageBody ?: it.messageBody.orEmpty() }
        val subId = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            SubscriptionManager.INVALID_SUBSCRIPTION_ID,
        )

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, first.timestampMillis)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            put(Telephony.Sms.PROTOCOL, first.protocolIdentifier)
            put(Telephony.Sms.REPLY_PATH_PRESENT, if (first.isReplyPathPresent) 1 else 0)
            first.serviceCenterAddress?.let { put(Telephony.Sms.SERVICE_CENTER, it) }
            put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        }

        // The provider assigns the conversation (thread) from the address.
        val uri = runCatching {
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        }.onFailure { Log.e(TAG, "Couldn't store incoming SMS", it) }.getOrNull()
        if (uri == null) Log.e(TAG, "Incoming SMS from $address was not stored")

        // Notify even if storing failed, so the text is at least seen.
        Notifications.showIncomingSms(context, address, body)
    }

    private companion object {
        const val TAG = "SmsDeliverReceiver"
    }
}
