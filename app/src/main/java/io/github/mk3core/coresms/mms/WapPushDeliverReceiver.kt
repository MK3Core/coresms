package io.github.mk3core.coresms.mms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import io.github.mk3core.coresms.notify.Notifications

/**
 * Receives MMS notifications from the carrier while CoreSMS is the default
 * SMS app.
 *
 * An MMS arrives as a small notice saying "a message is waiting at this URL";
 * the message itself has to be downloaded from the carrier. Once we receive
 * the notice, no other app will, so it must never be dropped.
 *
 * Downloading isn't built yet. Until it is, the raw notice is saved to disk
 * so it can be downloaded later, as long as the carrier still holds it.
 */
class WapPushDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        if (intent.type != MMS_MIME_TYPE) return

        val pdu = intent.getByteArrayExtra("data") ?: return
        val subId = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            SubscriptionManager.INVALID_SUBSCRIPTION_ID,
        )

        runCatching { SavedMmsNotices.save(context, pdu, subId) }
            .onFailure { Log.e(TAG, "Couldn't save MMS notice", it) }
        Notifications.showMmsSaved(context)
    }

    private companion object {
        const val TAG = "WapPushDeliverReceiver"
        const val MMS_MIME_TYPE = "application/vnd.wap.mms-message"
    }
}
