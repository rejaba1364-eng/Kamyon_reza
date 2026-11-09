package com.reza.kamyon

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind") ?: "document"
        val title = if (kind == "insurance") "یادآوری بیمه شخص ثالث" else "یادآوری معاینه فنی"
        val message = if (kind == "insurance") "موعد بیمه شخص ثالث خودروی شما نزدیک است؛ برای تمدید اقدام کنید." else "موعد معاینه فنی خودروی شما نزدیک است؛ برای دریافت نوبت اقدام کنید."
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("vehicle_reminders", "یادآوری‌های خودرو", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val openApp = PendingIntent.getActivity(
            context, 3000 + if (kind == "insurance") 1 else 2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, "vehicle_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(if (kind == "insurance") 4101 else 4102, notification)
    }
}
