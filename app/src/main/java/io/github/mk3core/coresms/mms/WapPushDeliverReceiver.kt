package io.github.mk3core.coresms.mms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives MMS notifications from the carrier while CoreSMS is the default
 * SMS app. An MMS arrives as a small notice saying "a message is waiting at
 * this URL"; once we receive it no other app will, so it's saved right away
 * (see [IncomingMms]) before the download is attempted.
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
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                IncomingMms.handlePush(context, pdu, subId)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val MMS_MIME_TYPE = "application/vnd.wap.mms-message"
    }
}
