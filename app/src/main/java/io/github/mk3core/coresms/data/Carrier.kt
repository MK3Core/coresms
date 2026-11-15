package io.github.mk3core.coresms.data

import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

object Carrier {

    /** The SIM's carrier name, like "Verizon", or null if the SIM doesn't say. */
    fun name(context: Context, subId: Int): String? = runCatching {
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return null
        val forSim = if (SubscriptionManager.isValidSubscriptionId(subId)) telephony.createForSubscriptionId(subId) else telephony
        forSim.simOperatorName?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
