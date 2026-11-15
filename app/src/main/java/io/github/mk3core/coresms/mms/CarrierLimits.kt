package io.github.mk3core.coresms.mms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/** The carrier's MMS limits, from Android's carrier configuration. */
data class CarrierLimits(
    val maxMessageBytes: Int,
    val maxImageWidth: Int,
    val maxImageHeight: Int,
) {
    companion object {
        // Android's own defaults when the carrier doesn't say.
        private const val DEFAULT_MAX_BYTES = 300 * 1024
        private const val DEFAULT_MAX_DIMENSION = 1600

        fun forSubscription(context: Context, subId: Int): CarrierLimits {
            val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED
            val config = if (!allowed) {
                null
            } else {
                runCatching {
                    val manager = context.getSystemService(CarrierConfigManager::class.java)
                    if (SubscriptionManager.isValidSubscriptionId(subId)) manager?.getConfigForSubId(subId) else manager?.config
                }.getOrNull()
            }
            fun int(key: String, default: Int) = config?.getInt(key, default)?.takeIf { it > 0 } ?: default
            return CarrierLimits(
                maxMessageBytes = int(CarrierConfigManager.KEY_MMS_MAX_MESSAGE_SIZE_INT, DEFAULT_MAX_BYTES),
                maxImageWidth = int(CarrierConfigManager.KEY_MMS_MAX_IMAGE_WIDTH_INT, DEFAULT_MAX_DIMENSION),
                maxImageHeight = int(CarrierConfigManager.KEY_MMS_MAX_IMAGE_HEIGHT_INT, DEFAULT_MAX_DIMENSION),
            )
        }
    }
}
