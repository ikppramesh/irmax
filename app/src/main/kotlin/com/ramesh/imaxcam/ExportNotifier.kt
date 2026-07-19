package com.ramesh.imaxcam

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

private const val CHANNEL_ID = "irmax_export"
const val NOTIFICATION_ID_PHOTO = 1001
const val NOTIFICATION_ID_VIDEO = 1002

/**
 * Progress notifications for photo/video export — video crop+watermark via Media3 Transformer can
 * take a few real seconds, so this gives feedback even if the user has looked away from the app.
 * Notifications are best-effort: if POST_NOTIFICATIONS was denied, these calls just silently no-op.
 */
object ExportNotifier {

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Export progress", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    fun showProgress(context: Context, id: Int, title: String, progress: Int, indeterminate: Boolean = false) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(title)
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    fun showDone(context: Context, id: Int, title: String, text: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    fun showError(context: Context, id: Int, title: String, text: String) {
        showDone(context, id, title, text)
    }

    fun cancel(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }
}
