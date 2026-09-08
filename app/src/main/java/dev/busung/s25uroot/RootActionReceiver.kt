package dev.busung.s25uroot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RootActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            RootNotificationManager.ACTION_ROOT_NOW -> {
                RootNotificationManager.cancelPending(context)
                val activityIntent = Intent(context, AutoRootActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(activityIntent)
            }
            RootNotificationManager.ACTION_DISMISS -> {
                RootNotificationManager.cancelPending(context)
            }
        }
    }
}
