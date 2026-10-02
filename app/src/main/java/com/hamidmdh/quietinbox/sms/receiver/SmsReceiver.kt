package com.hamidmdh.quietinbox.sms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.hamidmdh.quietinbox.sms.data.ContactUtils
import com.hamidmdh.quietinbox.sms.data.Prefs
import com.hamidmdh.quietinbox.sms.data.SmsRepository
import com.hamidmdh.quietinbox.sms.util.NotificationHelper

/**
 * Receives SMS_DELIVER (only delivered to the default SMS app).
 * Blocked senders are stored silently. Unsaved senders go to the
 * Unknown inbox with a muted (or silent) notification.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs.isNullOrEmpty()) return
        val address = msgs[0].originatingAddress.orEmpty()
        val body = msgs.joinToString("") { it.messageBody.orEmpty() }

        val prefs = Prefs(context)
        // We are the default SMS app: persist the message so it shows in inboxes.
        SmsRepository.insertInbox(context, address, body)

        if (prefs.isBlocked(address)) return // blocked: stored, never notified

        val isSaved = ContactUtils.isSavedNumber(context, address)
        val threadId = SmsRepository.threadIdFor(context, address)

        if (!isSaved && prefs.filterEnabled) {
            // Filtered to Unknown inbox.
            if (prefs.muteUnknown) return // muted: no notification at all
            NotificationHelper.showSms(context, address, body, threadId, silent = true)
        } else {
            NotificationHelper.showSms(context, address, body, threadId, silent = false)
        }
    }
}
