package io.github.mk3core.coresms.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony.Mms
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import io.github.mk3core.coresms.mmslib.pdu.PduHeaders

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
        Prefs.detectedNumber(context)?.let { numbers += it }
        return numbers.filter { it.isNotBlank() }.distinct()
    }

    fun forSubscription(context: Context, subId: Int): String? =
        fromSim(context, subId) ?: Prefs.myNumber(context) ?: Prefs.detectedNumber(context)

    /**
     * Many messaging apps record our own number as the sender of MMS we sent
     * (CoreSMS itself stores a placeholder the carrier fills in). Reading it
     * back from recent sent MMS covers SIMs that don't report their number.
     * Runs once at startup; the result is kept in [Prefs.detectedNumber].
     */
    fun detectFromHistory(context: Context) {
        if (Prefs.detectedNumber(context) != null) return
        val ids = mutableListOf<Long>()
        runCatching {
            context.contentResolver.query(
                Mms.CONTENT_URI,
                arrayOf(Mms._ID),
                "${Mms.MESSAGE_BOX} = ${Mms.MESSAGE_BOX_SENT}",
                null,
                "${Mms.DATE} DESC",
            )?.use { cursor -> while (cursor.moveToNext() && ids.size < 60) ids += cursor.getLong(0) }
        }
        val counts = HashMap<String, Int>()
        for (id in ids) {
            runCatching {
                context.contentResolver.query(
                    Uri.parse("content://mms/$id/addr"),
                    arrayOf(Mms.Addr.ADDRESS),
                    "${Mms.Addr.TYPE} = ${PduHeaders.FROM}",
                    null,
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val address = cursor.getString(0) ?: continue
                        if (address == PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR || !Phone.isValidRecipient(address)) continue
                        val key = counts.keys.firstOrNull { Phone.same(it, address) } ?: Phone.clean(address)
                        counts[key] = (counts[key] ?: 0) + 1
                    }
                }
            }
        }
        val best = counts.maxByOrNull { it.value } ?: return
        if (best.value >= 3) Prefs.setDetectedNumber(context, best.key)
    }

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
