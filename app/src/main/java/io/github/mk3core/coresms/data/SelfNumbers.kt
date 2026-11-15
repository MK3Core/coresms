package io.github.mk3core.coresms.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/**
 * Our own phone number(s). Group MMS lists every participant including us,
 * so we must recognise and remove ourselves, or every group message would
 * land in a separate thread that includes our own number.
 */
object SelfNumbers {

    fun all(context: Context): List<String> {
        val numbers = mutableListOf<String>()
        Prefs.myNumber(context)?.let { numbers += it }
        numbers += fromSims(context)
        return numbers.filter { it.isNotBlank() }.distinct()
    }

    fun forSubscription(context: Context, subId: Int): String? =
        fromSim(context, subId) ?: Prefs.myNumber(context)

    /** Numbers reported by the SIMs. Many SIMs don't store their number, so this can be empty. */
    fun fromSims(context: Context): List<String> {
        if (!hasPermission(context)) return emptyList()
        val subs = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        return runCatching {
            @SuppressLint("MissingPermission")
            val active = subs.activeSubscriptionInfoList.orEmpty()
            active.mapNotNull { fromSim(context, it.subscriptionId) }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    private fun fromSim(context: Context, subId: Int): String? {
        if (!hasPermission(context) || !SubscriptionManager.isValidSubscriptionId(subId)) return null
        val subs = context.getSystemService(SubscriptionManager::class.java) ?: return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                subs.getPhoneNumber(subId)
            } else {
                @Suppress("DEPRECATION")
                subs.getActiveSubscriptionInfo(subId)?.number
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun hasPermission(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_PHONE_NUMBERS
        } else {
            Manifest.permission.READ_PHONE_STATE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
    }
}
