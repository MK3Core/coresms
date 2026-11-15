package io.github.mk3core.coresms.sms

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager

/** The SmsManager for a SIM (or the default SIM when [subId] isn't valid). */
object SmsManagers {

    fun forSubscription(context: Context, subId: Int): SmsManager {
        val valid = SubscriptionManager.isValidSubscriptionId(subId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(SmsManager::class.java)
            if (valid) manager.createForSubscriptionId(subId) else manager
        } else {
            @Suppress("DEPRECATION")
            if (valid) SmsManager.getSmsManagerForSubscriptionId(subId) else SmsManager.getDefault()
        }
    }

    fun defaultSubscription(): Int = SubscriptionManager.getDefaultSmsSubscriptionId()
}
