package com.hamidmdh.quietinbox.sms.util

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import com.hamidmdh.quietinbox.sms.receiver.SmsDeliveredReceiver
import com.hamidmdh.quietinbox.sms.receiver.SmsSentReceiver
import java.util.UUID

object SmsSender {
    fun send(context: Context, destination: String, text: String) {
        val sm: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val parts = sm.divideMessage(text)
        val sendId = UUID.randomUUID().toString()
        MessageStatusStore.create(context, sendId, destination, text, parts.size)
        if (parts.size <= 1) {
            sm.sendTextMessage(
                destination, null, text,
                sentPI(context, sendId, 0), deliveredPI(context, sendId, 0)
            )
        } else {
            val sent = ArrayList<PendingIntent>(parts.size)
            val delivered = ArrayList<PendingIntent>(parts.size)
            parts.forEachIndexed { i, _ ->
                sent.add(sentPI(context, sendId, i))
                delivered.add(deliveredPI(context, sendId, i))
            }
            sm.sendMultipartTextMessage(destination, null, parts, sent, delivered)
        }
    }

    private fun reqCode(sendId: String, part: Int, kind: Int): Int =
        ((sendId.hashCode() * 31 + part) * 2 + kind) and 0x7fffffff

    private fun sentPI(context: Context, sendId: String, part: Int): PendingIntent {
        val i = Intent(context, SmsSentReceiver::class.java).apply {
            putExtra("send_id", sendId)
            putExtra("part", part)
        }
        return PendingIntent.getBroadcast(
            context, reqCode(sendId, part, 0), i,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun deliveredPI(context: Context, sendId: String, part: Int): PendingIntent {
        val i = Intent(context, SmsDeliveredReceiver::class.java).apply {
            putExtra("send_id", sendId)
            putExtra("part", part)
        }
        return PendingIntent.getBroadcast(
            context, reqCode(sendId, part, 1), i,
            PendingIntent.FLAG_IMMUTABLE
        )
    }
}
