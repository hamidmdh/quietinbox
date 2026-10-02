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

    // ---- Notification sound: null = system default, "NONE" (per-thread) = silent ----

    var globalSound: String?
        get() = sp.getString("sound_global", null)
        set(v) = sp.edit().putString("sound_global", v).apply()

    /** Null = follow global setting. */
    fun threadSound(threadId: Long): String? =
        if (!sp.contains("sound_t_$threadId")) null
        else sp.getString("sound_t_$threadId", null)

    fun setThreadSound(threadId: Long, uriOrNoneOrNull: String?) {
        if (uriOrNoneOrNull == null) sp.edit().remove("sound_t_$threadId").apply()
        else sp.edit().putString("sound_t_$threadId", uriOrNoneOrNull).apply()
    }

    // ---- Per-thread notification channel config tracking ----

    fun channelConfig(threadId: Long): String? =
        sp.getString("ch_cfg_$threadId", null)

    fun setChannelConfig(threadId: Long, key: String) =
        sp.edit().putString("ch_cfg_$threadId", key).apply()

    fun clearChannelConfig(threadId: Long) =
        sp.edit().remove("ch_cfg_$threadId").apply()

    fun clearAllChannelConfigs() {
        val ed = sp.edit()
        for (k in sp.all.keys) if (k.startsWith("ch_cfg_")) ed.remove(k)
        ed.apply()
    }

    // ---- Floating bubbles ----

    var bubblesEnabled: Boolean
        get() = sp.getBoolean("bubbles_enabled", true)
        set(v) = sp.edit().putBoolean("bubbles_enabled", v).apply()
}
