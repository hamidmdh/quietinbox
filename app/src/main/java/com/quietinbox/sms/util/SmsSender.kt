package com.quietinbox.sms.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager

object SmsSender {
    fun send(context: Context, destination: String, text: String) {
        val sm: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val parts = sm.divideMessage(text)
        if (parts.size <= 1) {
            sm.sendTextMessage(destination, null, text, null, null)
        } else {
            sm.sendMultipartTextMessage(destination, null, parts, null, null)
        }
    }
}
