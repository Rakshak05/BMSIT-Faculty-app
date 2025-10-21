package com.bmsit.faculty

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AlarmRingingService : Service() {

    private var ringtone: Ringtone? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra("MEETING_TITLE") ?: "Meeting Reminder"
        val location = intent?.getStringExtra("MEETING_LOCATION") ?: "Check app for details"

        ensureAlarmChannel()
        val notification = buildForegroundNotification(title, location)

        // Start foreground immediately to avoid background kill
        startForeground(NOTIF_ID, notification)

        // Start alarm sound (looping)
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        } catch (_: Exception) { }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try { ringtone?.stop() } catch (_: Exception) {}
        ringtone = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureAlarmChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Meeting Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(title: String, location: String): Notification {
        val stopIntent = Intent(this, AlarmActionReceiver::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getBroadcast(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val snoozeIntent = Intent(this, AlarmActionReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra("SNOOZE_MINUTES", 5)
            putExtra("MEETING_TITLE", title)
            putExtra("MEETING_LOCATION", location)
        }
        val snoozePi = PendingIntent.getBroadcast(
            this,
            1,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val openIntent = Intent(this, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(
            this,
            2,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Your meeting is starting soon at $location")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(0, "Stop", stopPi)
            .addAction(0, "Snooze", snoozePi)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "meeting_reminders_alarm"
        private const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.bmsit.faculty.ACTION_STOP_ALARM"
        const val ACTION_SNOOZE = "com.bmsit.faculty.ACTION_SNOOZE_ALARM"
    }
}
