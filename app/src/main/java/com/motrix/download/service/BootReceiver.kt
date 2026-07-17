package com.motrix.download.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.motrix.download.data.datastore.PreferenceDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        CoroutineScope(Dispatchers.IO).launch {
            val prefs = PreferenceDataStore(context)
            val resumeOnStart = prefs.resumeAllOnStart.first()
            if (resumeOnStart) {
                DownloadForegroundService.startEngine(context)
            }
        }
    }
}
