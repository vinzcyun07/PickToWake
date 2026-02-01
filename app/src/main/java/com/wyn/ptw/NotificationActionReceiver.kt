package com.wyn.ptw

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_STOP = "com.wyn.ptw.ACTION_STOP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP) {
            SettingsStore.setEnabled(context, false)
            WakeService.stop(context)
        }
    }
}
