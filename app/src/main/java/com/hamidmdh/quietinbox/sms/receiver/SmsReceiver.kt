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
 * Classifies the sender: saved contact -> loud notification,
 * unsaved -> silent / mutedUnknown ? no notification : silent notification.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs.isNullOrEmpty()) return
        val address = msgs[0].originatingAddress.orEmpty()
        val body = msgs.joinToString("") { it.messageBody.orEmpty() }

        val prefs = Prefs(context)
        val isSaved = ContactUtils.isSavedNumber(context, address)
        val threadId = SmsRepository.threadIdFor(context, address)

        // We are the default SMS app: persist the message so it shows in inboxes.
        SmsRepository.insertInbox(context, address, body)

        if (!isSaved && prefs.filterEnabled) {
            // Filtered to Unknown inbox.
            if (prefs.muteUnknown) {
                // Muted: do not notify at all.
                return
            }
            NotificationHelper.showSms(context, address, body, threadId, silent = true)
        } else {
            NotificationHelper.showSms(context, address, body, threadId, silent = false)
        }
    }
}
