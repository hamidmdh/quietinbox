package com.hamidmdh.quietinbox.sms.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hamidmdh.quietinbox.sms.ConversationActivity
import com.hamidmdh.quietinbox.sms.data.ContactUtils
import com.hamidmdh.quietinbox.sms.data.Prefs

object NotificationHelper {
    const val CH_KNOWN = "sms_known"
    const val CH_UNKNOWN = "sms_unknown"
    private const val CH_THREAD_PREFIX = "sms_t_"

    private fun audioAttrs(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()

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

    /**
     * Per-conversation channel so each thread can carry its own custom sound.
     * Channel sound is fixed at creation, so the channel is recreated whenever
     * the configured sound changes.
     */
    private fun threadChannelId(context: Context, prefs: Prefs, threadId: Long, address: String): String {
        val id = CH_THREAD_PREFIX + threadId
        val sound = prefs.threadSound(threadId) ?: prefs.globalSound // null = default
        val configKey = (sound ?: "DEFAULT") + "|" + ContactUtils.displayName(context, address)
        if (prefs.channelConfig(threadId) != configKey) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    nm.deleteNotificationChannel(id)
                    nm.createNotificationChannel(
                        NotificationChannel(
                            id,
                            ContactUtils.displayName(context, address),
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            if (sound != null) setSound(Uri.parse(sound), audioAttrs())
                        }
                    )
                }
            } catch (_: Exception) {}
            prefs.setChannelConfig(threadId, configKey)
        }
        return id
    }

    fun deleteThreadChannel(context: Context, threadId: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.deleteNotificationChannel(CH_THREAD_PREFIX + threadId)
            }
        } catch (_: Exception) {}
        Prefs(context).clearChannelConfig(threadId)
    }

    fun showSms(
        context: Context,
        address: String,
        body: String,
        threadId: Long,
        silent: Boolean
    ) {
        ensureChannels(context)
        val prefs = Prefs(context)
        val perThread = prefs.threadSound(threadId)
        // "NONE" override forces silence even for saved contacts.
        val effectiveSilent = silent || perThread == "NONE"
        val channel = if (effectiveSilent) {
            CH_UNKNOWN
        } else {
            threadChannelId(context, prefs, threadId, address)
        }
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
        val notif = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .apply {
                if (effectiveSilent) {
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
