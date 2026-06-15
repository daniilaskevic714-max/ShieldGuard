package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.database.ShieldGuardDatabase
import com.example.data.model.QuarantinedItem
import com.example.data.model.ScanReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class ShieldGuardService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    companion object {
        const val CHANNEL_ID_PROTECTION = "shield_guard_realtime_protection"
        const val CHANNEL_ID_ALERTS = "shield_guard_security_alerts"
        const val NOTIFICATION_ID_FOREGROUND = 101
        const val NOTIFICATION_ID_ALERT = 202

        var isRunning = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, ShieldGuardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ShieldGuardService::class.java)
            context.stopService(intent)
        }

        // Helper to simulate a Real-time Download scan from anywhere
        fun simulateDownloadScan(context: Context, fileName: String, containsVirus: Boolean) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Post "Scanning..." notification
            val progressNotification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setContentTitle("Сканирование новой загрузки")
                .setContentText("Проверка файла: $fileName...")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setProgress(100, 45, true)
                .build()
                
            notificationManager.notify(NOTIFICATION_ID_ALERT, progressNotification)

            // Delayed real action
            CoroutineScope(Dispatchers.IO).launch {
                delay(2000)
                if (containsVirus) {
                    val virusName = if (fileName.contains("adware", true)) "Adware.Dropper" else "Trojan.AndroidOS.Generic"
                    
                    // Add to room database
                    val db = ShieldGuardDatabase.getDatabase(context)
                    val dao = db.shieldGuardDao()
                    
                    val qId = dao.insertQuarantinedItem(
                        QuarantinedItem(
                            fileName = fileName,
                            filePath = "/storage/emulated/0/Download/$fileName",
                            threatType = virusName,
                            isAutomaticallyRemoved = true,
                            sizeBytes = Random.nextLong(100_000, 8_000_000)
                        )
                    )

                    dao.insertScanReport(
                        ScanReport(
                            scanType = "Real-time Download",
                            filesScannedCount = 1,
                            threatsFoundCount = 1,
                            cacheCleanedMb = 0f,
                            isInternetSourced = true,
                            status = "Threats Quarantined"
                        )
                    )

                    // Post threat warning
                    val notificationIntent = Intent(context, MainActivity::class.java).apply {
                        putExtra("NAV_TARGET", "quarantine")
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context, 0, notificationIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val alertNotification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
                        .setSmallIcon(android.R.drawable.stat_notify_error)
                        .setContentTitle("⚠️ Угроза заблокирована до запуска!")
                        .setContentText("Обнаружен вирус: $virusName в файле $fileName. Он автоматически перемещен в карантин.")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(
                            "Вредоносный скачанный файл заблокирован до запуска! Файл $fileName идентифицирован как $virusName и изолирован в Карантин для защиты ваших личных данных."
                        ))
                        .build()
                    
                    notificationManager.notify(NOTIFICATION_ID_ALERT, alertNotification)
                } else {
                    // Safe download
                    val safeNotification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle("Файл проверен и безопасен")
                        .setContentText("Загрузка $fileName чиста. Угроз не обнаружено.")
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setAutoCancel(true)
                        .build()
                    
                    notificationManager.notify(NOTIFICATION_ID_ALERT, safeNotification)
                }
            }
        }

        // Helper to simulate launch warning of a suspicious app
        fun triggerAppEntranceWarning(context: Context, appName: String, packageName: String) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val notificationIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("NAV_TARGET", "permissions")
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 1, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alert = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🚨 Риск! Вход в подозрительное приложение")
                .setContentText("Приложение '$appName' собирает скрытые личные данные.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(Notification.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "Внимание! Вы только что запустили подозрительное приложение '$appName' ($packageName). Оно зафиксировано нашей системой мониторинга угроз как рекламное ПО с избыточными разрешениями, угрожающее личным данным."
                ))
                .build()

            notificationManager.notify(NOTIFICATION_ID_ALERT + 2, alert)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground with shield indication
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID_PROTECTION)
            .setContentTitle("ShieldGuard Активен")
            .setContentText("Защита файлов и сайтов в реальном времени включена")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID_FOREGROUND,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID_FOREGROUND, notification)
        }

        // Simulate continuous low battery checks or file monitoring loop
        scope.launch {
            while (true) {
                delay(120_000) // check downloads, system status logs every 2 mins
                // Continuous background silent scan simulation for low battery
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Channel 1: Foreground guard status representation
            val nameProtection = "Фоновая защита в реальном времени"
            val descProtection = "Отображает текущий статус антивирусного сканера"
            val channelProtection = NotificationChannel(
                CHANNEL_ID_PROTECTION,
                nameProtection,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = descProtection
                setShowBadge(false)
            }
            manager.createNotificationChannel(channelProtection)

            // Channel 2: Real-time threat alerts
            val nameAlerts = "Предупреждения об угрозах"
            val descAlerts = "Мгновенные оповещения при обнаружении вирусов, рекламы или вредоносных сайтов"
            val channelAlerts = NotificationChannel(
                CHANNEL_ID_ALERTS,
                nameAlerts,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = descAlerts
                enableVibration(true)
                enableLights(true)
            }
            manager.createNotificationChannel(channelAlerts)
        }
    }
}
