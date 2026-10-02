package com.quietinbox.sms.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.quietinbox.sms.ConversationActivity
import com.quietinbox.sms.R
import com.quietinbox.sms.data.ContactUtils

object NotificationHelper {
    const val CH_KNOWN = "sms_known"
    const val CH_UNKNOWN = "sms_unknown"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_KNOWN, "Messages", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CH_UNKNOWN, "Unknown senders",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    fun showSms(
        context: Context,
        address: String,
        body: String,
        threadId: Long,
        silent: Boolean
    ) {
        ensureChannels(context)
        val intent = Intent(context, ConversationActivity::class.java).apply {
            putExtra(ConversationActivity.EXTRA_THREAD_ID, threadId)
            putExtra(ConversationActivity.EXTRA_ADDRESS, address)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, threadId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = ContactUtils.displayName(context, address)
        val channel = if (silent) CH_UNKNOWN else CH_KNOWN
        val notif = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .apply {
                if (silent) {
                    setSilent(true)
                    setOnlyAlertOnce(true)
                } else {
                    setPriority(NotificationCompat.PRIORITY_HIGH)
                    setDefaults(NotificationCompat.DEFAULT_ALL)
                }
            }
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(threadId.toInt(), notif)
    }
}
