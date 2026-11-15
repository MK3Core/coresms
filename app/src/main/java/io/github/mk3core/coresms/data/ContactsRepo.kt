package io.github.mk3core.coresms.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.PhoneLookup
import android.util.LruCache
import androidx.core.content.ContextCompat

/** A person as shown in CoreSMS: their saved name and photo, or just the number. */
data class Contact(
    val address: String,
    val name: String?,
    val photoUri: Uri?,
) {
    val displayName: String get() = name ?: address
}

/** Looks up contact names and photos for numbers, with a cache cleared when contacts change. */
object ContactsRepo {

    private val cache = LruCache<String, Contact>(500)
    @Volatile private var observing = false

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    fun lookup(context: Context, address: String): Contact {
        cache.get(address)?.let { return it }
        val contact = query(context, address)
        // Don't cache misses before permission is granted, or names would
        // stay missing until the app restarts.
        if (hasPermission(context)) {
            cache.put(address, contact)
            observe(context)
        }
        return contact
    }

    fun lookupAll(context: Context, addresses: List<String>): List<Contact> = addresses.map { lookup(context, it) }

    fun clearCache() = cache.evictAll()

    private fun query(context: Context, address: String): Contact {
        if (address.isBlank() || !hasPermission(context)) return Contact(address, null, null)
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_THUMBNAIL_URI),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    Contact(address, cursor.getString(0), cursor.getString(1)?.let(Uri::parse))
                } else {
                    null
                }
            }
        }.getOrNull() ?: Contact(address, null, null)
    }

    private fun observe(context: Context) {
        if (observing) return
        observing = true
        context.applicationContext.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = clearCache()
            },
        )
    }

    /** Contacts with phone numbers matching [query] by name or number, for the new-message screen. */
    fun search(context: Context, query: String, limit: Int = 50): List<Contact> {
        if (!hasPermission(context) || query.isBlank()) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(query))
        val results = mutableListOf<Contact>()
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            )?.use { cursor ->
                while (cursor.moveToNext() && results.size < limit) {
                    val number = cursor.getString(1) ?: continue
                    if (results.any { Phone.same(it.address, number) && it.name == cursor.getString(0) }) continue
                    results += Contact(number, cursor.getString(0), cursor.getString(2)?.let(Uri::parse))
                }
            }
        }
        return results
    }
}
