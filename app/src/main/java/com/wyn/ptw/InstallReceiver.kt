package com.wyn.ptw

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == Intent.ACTION_PACKAGE_ADDED) {
            SettingsStore.setEnabled(context, true)
            WakeService.start(context)
        }
    }
}
