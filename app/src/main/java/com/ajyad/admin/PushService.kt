package com.ajyad.admin

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives data-only messages from the store (title, body, url, tag) and shows
 * the alert itself, so the ringtone channel is used whether the app is open,
 * in the background or closed.
 */
class PushService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: getString(R.string.app_name)
        val body = data["body"] ?: ""
        val url = data["url"] ?: "/admin"
        val tag = data["tag"] ?: "ajyad"

        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_URL, url)
        }
        val pending = PendingIntent.getActivity(
            this, tag.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(this, AjyadApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        getSystemService(NotificationManager::class.java).notify(tag.hashCode(), notification)
    }

    // The new token is sent to the store the next time the admin pages open
    // while signed in (see MainActivity), so nothing to do here.
    override fun onNewToken(token: String) {
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit().remove(MainActivity.KEY_REGISTERED_AT).apply()
    }
}
