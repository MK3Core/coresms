package io.github.mk3core.coresms.notify

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import androidx.core.content.ContextCompat

object Contacts {

    /** The contact name saved for this number, or null if there isn't one (or no permission). */
    fun displayName(context: Context, address: String): String? {
        if (address.isBlank()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
        return runCatching {
            context.contentResolver.query(uri, arrayOf(PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
    }
}
