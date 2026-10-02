package com.hamidmdh.quietinbox.sms.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("quietinbox", Context.MODE_PRIVATE)

    var filterEnabled: Boolean
        get() = sp.getBoolean("filter_enabled", true)
        set(v) = sp.edit().putBoolean("filter_enabled", v).apply()

    var muteUnknown: Boolean
        get() = sp.getBoolean("mute_unknown", true)
        set(v) = sp.edit().putBoolean("mute_unknown", v).apply()

    companion object {
        const val FILTER_UNKNOWN = 0
        const val TAB_KNOWN = 0
        const val TAB_UNKNOWN = 1
        const val TAB_ALL = 2
    }
}
