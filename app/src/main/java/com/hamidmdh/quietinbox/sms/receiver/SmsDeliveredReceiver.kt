package com.hamidmdh.quietinbox.sms.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hamidmdh.quietinbox.sms.util.MessageStatusStore

/** Fired per message part when the carrier confirms delivery to the handset. */
class SmsDeliveredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (resultCode != Activity.RESULT_OK) return // still sent, just unconfirmed
        val sendId = intent.getStringExtra("send_id") ?: return
        val part = intent.getIntExtra("part", 0)
        MessageStatusStore.markDelivered(context, sendId, part)
    }
}
