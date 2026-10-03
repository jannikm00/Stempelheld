package com.jannik.stempelheld

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Stop logic: If the user clicked the notification to stop it
        if (intent.action == "STOP_ALARM") {
            val stopIntent = Intent(context, AlarmReceiver::class.java)
            val stopPending = PendingIntent.getBroadcast(context, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
            alarmManager.cancel(stopPending)
            notificationManager.cancel(101)
            return
        }

        // Show the notification
        showNotification(context, notificationManager)

        // "Nerven" Logic: Read preference and reschedule if enabled
        val prefs = context.getSharedPreferences("alarm_prefs", Context.MODE_PRIVATE)
        val isRepeating = prefs.getBoolean("is_repeating", false)

        if (isRepeating) {
            val repeatIntent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, repeatIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            // Reschedule for 1 second later
            val nextTrigger = System.currentTimeMillis() + 1000
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pendingIntent)
        }
    }

    private fun showNotification(context: Context, manager: NotificationManager) {
        val stopIntent = Intent(context, AlarmReceiver::class.java).apply { action = "STOP_ALARM" }
        val stopPending = PendingIntent.getBroadcast(context, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(context, "reminder_channel")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Ausstempeln!")
            .setContentText("Zeit zu Gehen. Tippen zum Stoppen.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(stopPending) // Clicking the notification stops the cycle
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stoppen", stopPending)

        manager.notify(101, builder.build())
    }
}