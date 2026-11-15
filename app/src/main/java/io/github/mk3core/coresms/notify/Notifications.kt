package io.github.mk3core.coresms.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import io.github.mk3core.coresms.MainActivity
import io.github.mk3core.coresms.R

object Notifications {

    private const val CHANNEL_MESSAGES = "messages"
    private const val CHANNEL_MMS = "mms"
    private const val CHANNEL_ERRORS = "errors"

    private const val ID_MMS_SAVED = 1

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_MESSAGES,
                    context.getString(R.string.channel_messages),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
                NotificationChannel(
                    CHANNEL_MMS,
                    context.getString(R.string.channel_mms),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
                NotificationChannel(
                    CHANNEL_ERRORS,
                    context.getString(R.string.channel_errors),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            ),
        )
    }

    fun showIncomingSms(context: Context, address: String, body: String) {
        val name = Contacts.displayName(context, address) ?: address
        val sender = Person.Builder().setName(name).setKey(address).build()
        val me = Person.Builder().setName(context.getString(R.string.notification_me)).build()
        val style = NotificationCompat.MessagingStyle(me)
            .addMessage(body, System.currentTimeMillis(), sender)

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        post(context, address.hashCode(), notification)
    }

    fun showMmsSaved(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_MMS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_mms_saved_title))
            .setContentText(context.getString(R.string.notification_mms_saved_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notification_mms_saved_text)),
            )
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        post(context, ID_MMS_SAVED, notification)
    }

    fun showSendFailed(context: Context, address: String) {
        val name = Contacts.displayName(context, address) ?: address
        val notification = NotificationCompat.Builder(context, CHANNEL_ERRORS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_send_failed_title))
            .setContentText(context.getString(R.string.notification_send_failed_text, name))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        post(context, "failed:$address".hashCode(), notification)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // The permission is checked on the line above notify().
    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
