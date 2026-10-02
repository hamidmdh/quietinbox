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

    fun getBlocked(): Set<String> =
        sp.getStringSet("blocked", emptySet())?.toSet() ?: emptySet()

    fun isBlocked(address: String?): Boolean {
        if (address.isNullOrBlank()) return false
        val set = getBlocked()
        if (set.contains(address)) return true
        val d = norm(address)
        return set.any { norm(it) == d }
    }

    fun setBlocked(address: String, blocked: Boolean) {
        val s = getBlocked().toMutableSet()
        if (blocked) {
            s.add(address)
        } else {
            val d = norm(address)
            s.removeAll { it == address || norm(it) == d }
        }
        sp.edit().putStringSet("blocked", s).apply()
    }

    private fun norm(a: String): String =
        if (a.any { it.isLetter() }) a.uppercase() else a.filter { it.isDigit() }
}
