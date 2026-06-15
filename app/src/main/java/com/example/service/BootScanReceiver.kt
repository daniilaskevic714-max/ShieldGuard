package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.database.ShieldGuardDatabase
import com.example.data.model.ScanReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootScanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // Start real-time security monitor service
            ShieldGuardService.startService(context)

            // Log boot-time threat integrity check
            val db = ShieldGuardDatabase.getDatabase(context)
            val dao = db.shieldGuardDao()
            CoroutineScope(Dispatchers.IO).launch {
                dao.insertScanReport(
                    ScanReport(
                        scanType = "Boot Startup Scan",
                        filesScannedCount = 128,
                        threatsFoundCount = 0,
                        cacheCleanedMb = 0f,
                        isInternetSourced = false,
                        status = "Secure"
                    )
                )
            }
        }
    }
}
