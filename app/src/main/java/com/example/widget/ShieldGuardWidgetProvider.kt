package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

class ShieldGuardWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_SCAN) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("WIDGET_TRIGGER_SCAN", true)
            }
            context.startActivity(launchIntent)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastScore = prefs.getInt(KEY_LAST_SCORE, -1)
        val lastStatus = if (lastScore >= 0) {
            "Индекс защиты: $lastScore/100"
        } else {
            "Нажмите для аудита"
        }
        val statusColor = when {
            lastScore < 0 -> Color.parseColor("#94A3B8")
            lastScore >= 80 -> Color.parseColor("#10B981")
            lastScore >= 55 -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#EF4444")
        }
        views.setTextViewText(R.id.widget_state, lastStatus)
        views.setTextColor(R.id.widget_state, statusColor)

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        val scanIntent = Intent(context, ShieldGuardWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_SCAN
        }
        val scanPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            scanIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_icon, scanPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        private const val ACTION_WIDGET_SCAN = "com.example.ACTION_WIDGET_SCAN"
        const val PREFS_NAME = "shield_guard_prefs"
        const val KEY_LAST_SCORE = "widget_last_posture_score"

        fun updateWidgetScore(context: Context, score: Int) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LAST_SCORE, score)
                .apply()

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = ComponentName(context.packageName, ShieldGuardWidgetProvider::class.java.name)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            if (appWidgetIds.isEmpty()) return

            val intent = Intent(context, ShieldGuardWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            context.sendBroadcast(intent)
        }
    }
}
