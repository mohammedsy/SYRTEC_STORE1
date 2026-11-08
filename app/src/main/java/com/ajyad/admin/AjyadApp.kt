package com.ajyad.admin

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.app.Notification

/**
 * Creates the alert channel once. Android lets the app set a channel's sound only
 * when it is created, so a change of sound means a new id (bump the suffix).
 */
class AjyadApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = getString(R.string.channel_description)
            setSound(sound, audio)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 800)
            enableLights(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "orders_v1"
    }
}
