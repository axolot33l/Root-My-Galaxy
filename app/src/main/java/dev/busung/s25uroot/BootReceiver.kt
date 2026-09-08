package dev.busung.s25uroot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!AutoRootPreferences.isEnabled(context)) return
        if (NativeProbe.isKernelSuActive()) return
        RootNotificationManager.createChannel(context)
        RootNotificationManager.showPendingRoot(context)
    }
}
