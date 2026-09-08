package dev.busung.s25uroot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object RootNotificationManager {
    private const val CHANNEL_ID = "auto_root"
    private const val CHANNEL_NAME = "Auto Root"
    private const val NOTIFY_ID_PENDING = 1001
    private const val NOTIFY_ID_DONE = 1003
    const val ACTION_ROOT_NOW = "dev.busung.s25uroot.ROOT_NOW"
    const val ACTION_DISMISS = "dev.busung.s25uroot.DISMISS"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Auto-root notifications"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun showPendingRoot(context: Context) {
        val rootIntent = Intent(context, RootActionReceiver::class.java).apply {
            action = ACTION_ROOT_NOW
        }
        val rootPending = PendingIntent.getBroadcast(
            context, 0, rootIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissIntent = Intent(context, RootActionReceiver::class.java).apply {
            action = ACTION_DISMISS
        }
        val dismissPending = PendingIntent.getBroadcast(
            context, 1, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val activityIntent = Intent(context, AutoRootActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val activityPending = PendingIntent.getActivity(
            context, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Root available")
            .setContentText("Tap to root your device")
            .setContentIntent(activityPending)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Root is not active. Tap to open the app and start rooting."
            ))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_lock_lock, "Root Now", rootPending)
            .addAction(android.R.drawable.ic_menu_revert, "Dismiss", dismissPending)
            .setAutoCancel(false)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFY_ID_PENDING, notification)
    }

    fun showSuccess(context: Context) {
        cancelPending(context)
        AutoRootPreferences.resetRetryCount(context)

        val ksuIntent = context.packageManager
            .getLaunchIntentForPackage("me.weishu.kernelsu")
            ?: Intent()
        val ksuPending = PendingIntent.getActivity(
            context, 0, ksuIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Root successful!")
            .setContentText("KernelSU is active. Tap to open KernelSU Manager.")
            .setContentIntent(ksuPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFY_ID_DONE, notification)
    }

    fun showFailed(context: Context, reason: String) {
        cancelPending(context)

        val activityIntent = Intent(context, AutoRootActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val activityPending = PendingIntent.getActivity(
            context, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Root failed")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setContentIntent(activityPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFY_ID_DONE, notification)
    }

    fun cancelPending(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(NOTIFY_ID_PENDING)
    }

    fun cancelAll(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }
}
