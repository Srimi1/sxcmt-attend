package `in`.attend.app.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import `in`.attend.app.AttendApplication
import `in`.attend.app.MainActivity
import `in`.attend.app.R
import `in`.attend.core.MarkingStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Daily "any absences?" nudge at the student's chosen time. Inexact alarms only, so no exact-alarm permission is needed;
 * the next one is scheduled when each fires and again after a restart. Nothing leaves the phone.
 */
object Reminders {
    private const val CHANNEL = "daily-check"
    private const val WINDOW_MS = 10 * 60 * 1000L

    fun schedule(context: Context, time: LocalTime?, now: LocalDateTime = LocalDateTime.now()) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context)
        alarms.cancel(pending)
        if (time == null) return
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        alarms.setWindow(AlarmManager.RTC_WAKEUP, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), WINDOW_MS, pending)
    }

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    internal fun notify(context: Context, assumePresent: Boolean) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Daily attendance check", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = if (assumePresent) "Today's periods count as present. Tap to mark any you missed." else "Tap to mark today's periods."
        manager.notify(1, Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_attend).setContentTitle("Any absences today?")
            .setContentText(text).setContentIntent(open).setAutoCancel(true).build())
    }

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(context, 0, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val data = (context.applicationContext as AttendApplication).repository.data.first()
                val today = LocalDate.now()
                // Teaching days only: skip Sundays, holidays, closures and days already inside the sheet totals.
                val covered = data.sheetBaseline?.asOf?.let { today <= it } == true
                if (!covered && data.periodsOn(today).any { !data.isNoClass(today, it.session.id) })
                    Reminders.notify(context, data.markingStyle == MarkingStyle.ASSUME_PRESENT)
                Reminders.schedule(context, data.reminderTime)
            } finally { result.finish() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { Reminders.schedule(context, (context.applicationContext as AttendApplication).repository.data.first().reminderTime) }
            finally { result.finish() }
        }
    }
}
