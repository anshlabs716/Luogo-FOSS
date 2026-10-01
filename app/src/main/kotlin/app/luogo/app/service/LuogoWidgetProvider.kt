package app.luogo.app.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.luogo.app.LuogoApplication
import app.luogo.app.MainActivity
import app.luogo.app.R

/**
 * Home-screen widget displaying live E2EE location sharing status, current accuracy & source,
 * and quick action to open Luogo-FOSS.
 */
class LuogoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val repo = runCatching { LuogoApplication.getRepository(context) }.getOrNull()
        val sharing = repo?.userProfile?.value?.sharingEnabled ?: true
        val fix = repo?.hardwareLocationManager?.fusedLocation?.value
        val summary = if (fix != null) {
            "${if (sharing) "LIVE" else "PAUSED"} · ±${fix.horizontalAccuracyMeters.toInt()}m (${fix.sourceSummary})"
        } else {
            if (sharing) "Luogo-FOSS · Live E2EE Active" else "Luogo-FOSS · Sharing Paused"
        }

        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.luogo_widget_layout)
            views.setTextViewText(R.id.widget_title, "Luogo-FOSS")
            views.setTextViewText(R.id.widget_subtitle, summary)

            val openIntent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context,
                widgetId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
