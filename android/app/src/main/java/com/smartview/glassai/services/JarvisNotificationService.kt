package com.smartview.glassai.services

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Listens for incoming notifications and makes them available to the
 * Jarvis assistant for summarization or action.
 *
 * To enable: Settings → Notification Access → enable "LocalMeta"
 */
class JarvisNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "JarvisNotif"
        var lastNotifications: List<NotificationEntry> = emptyList()
            private set
    }

    data class NotificationEntry(
        val packageName: String,
        val title: String,
        val text: String,
        val time: Long
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val title = notification.extras?.getString(Notification.EXTRA_TITLE) ?: ""
        val text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        } else {
            @Suppress("DEPRECATION")
            notification.extras?.getString(Notification.EXTRA_TEXT) ?: ""
        }
        val entry = NotificationEntry(
            packageName = sbn.packageName,
            title = title,
            text = text,
            time = sbn.postTime
        )
        lastNotifications = listOf(entry) + lastNotifications.take(19)
        Log.d(TAG, "Notification from ${sbn.packageName}: $title")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Optional: track removed notifications
    }
}
