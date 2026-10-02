package com.quietinbox.sms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Stub required for default-SMS-app eligibility. MMS handling omitted for v1. */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}
}
