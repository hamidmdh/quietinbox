package com.quietinbox.sms.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Stub required for default-SMS-app eligibility (RESPOND_VIA_MESSAGE). */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
