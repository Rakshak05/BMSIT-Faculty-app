package com.bmsit.faculty

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .update(mapOf("fcmToken" to token))
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val type = message.data["type"]
        
        // Handle refresh notifications
        if (type == "refresh_dashboard") {
            // Send a broadcast to refresh the dashboard
            val intent = Intent("REFRESH_DASHBOARD")
            intent.setPackage(applicationContext.packageName)
            applicationContext.sendBroadcast(intent)
            return
        }
        
        // Handle meeting start notifications
        if (type == "meeting_start") {
            val title = message.data["title"] ?: "Meeting Starting"
            val location = message.data["location"] ?: ""
            val body = if (location.isNotEmpty()) {
                "The meeting \"$title\" is starting now at $location."
            } else {
                "The meeting \"$title\" is starting now."
            }
            showNotification(title, body)
            return
        }
        
        val titleFromData = message.data["title"]
        val bodyFromData = message.data["body"]
        val notifTitle = message.notification?.title
        val notifBody = message.notification?.body

        val (title, body) = when (type) {
            "created" -> Pair(
                titleFromData ?: notifTitle ?: "New meeting",
                bodyFromData ?: notifBody ?: "A new meeting has been scheduled."
            )
            "cancelled" -> Pair(
                titleFromData ?: "Meeting cancelled",
                bodyFromData ?: notifBody ?: "A scheduled meeting has been cancelled."
            )
            "meeting_cancelled" -> Pair(
                titleFromData ?: "Meeting cancelled",
                bodyFromData ?: notifBody ?: "A scheduled meeting has been cancelled."
            )
            "rescheduled" -> Pair(
                titleFromData ?: "Meeting rescheduled",
                bodyFromData ?: notifBody ?: "A scheduled meeting has been rescheduled."
            )
            else -> Pair(titleFromData ?: notifTitle ?: "Meeting update", bodyFromData ?: notifBody ?: "There is an update to a meeting.")
        }
        showNotification(title, body)
    }

    private fun ensureNotifChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "meetings_channel"
            val name = "Meetings"
            val desc = "Meeting notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance)
            channel.description = desc
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, body: String) {
        val ctx = applicationContext
        ensureNotifChannel(ctx)
        
        // Check for notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                // Cannot show notification without permission, silently return
                return
            }
        }

        val intent = Intent(ctx, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            ctx,
            (title + body).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val builder = NotificationCompat.Builder(ctx, "meetings_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        try {
            with(NotificationManagerCompat.from(ctx)) {
                notify((title + body).hashCode(), builder.build())
            }
        } catch (e: SecurityException) {
            // Handle the case where permission is denied at runtime
            // This can happen even if we checked above, as permissions can be revoked
        }
    }
}