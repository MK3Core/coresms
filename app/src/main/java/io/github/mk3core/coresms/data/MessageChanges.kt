package io.github.mk3core.coresms.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/** Emits whenever messages change (in any app) or CoreSMS's own state changes. */
object MessageChanges {

    private val local = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Signals a change that the system database doesn't know about, like pinning. */
    fun notifyLocalChange() {
        local.tryEmit(Unit)
    }

    @OptIn(FlowPreview::class)
    fun observe(context: Context): Flow<Unit> {
        val provider = callbackFlow {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }
            val resolver = context.applicationContext.contentResolver
            listOf("content://mms-sms/", "content://sms/", "content://mms/").forEach {
                resolver.registerContentObserver(Uri.parse(it), true, observer)
            }
            awaitClose { resolver.unregisterContentObserver(observer) }
        }
        return merge(provider, local).debounce(150).onStart { emit(Unit) }
    }
}
