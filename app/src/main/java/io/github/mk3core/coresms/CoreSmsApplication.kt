package io.github.mk3core.coresms

import android.app.Application
import io.github.mk3core.coresms.notify.Notifications

class CoreSmsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
