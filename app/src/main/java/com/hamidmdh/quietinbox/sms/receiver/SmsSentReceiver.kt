package com.hamidmdh.quietinbox.sms.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hamidmdh.quietinbox.sms.util.MessageStatusStore

/** Fired per message part when the radio accepts (or rejects) an outgoing SMS. */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sendId = intent.getStringExtra("send_id") ?: return
        val part = intent.getIntExtra("part", 0)
        if (resultCode == Activity.RESULT_OK) {
            MessageStatusStore.markSent(context, sendId, part)
        } else {
            MessageStatusStore.markFailed(context, sendId, part)
        }
    }
}
