package io.github.mk3core.coresms.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Sends the canned "Can't talk now" style replies you can pick when
 * declining a call. Runs without any UI.
 */
class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val recipients = SmsUri.recipients(intent.data?.schemeSpecificPart)
            if (!text.isNullOrBlank()) {
                recipients.forEach { address ->
                    runCatching { SmsSender.send(this, address, text) }
                        .onFailure { Log.e(TAG, "Quick reply failed", it) }
                }
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private companion object {
        const val TAG = "HeadlessSmsSendService"
    }
}
