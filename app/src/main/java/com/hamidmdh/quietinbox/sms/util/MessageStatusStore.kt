package com.hamidmdh.quietinbox.sms.util

import android.content.Context
import org.json.JSONObject

/**
 * Tracks per-send SMS state across process restarts so the thread view can
 * render ticks: sending (…) -> sent (✓) -> delivered (✓✓), or failed (!).
 * Carriers don't always return delivery reports; without one it stays at ✓.
 */
object MessageStatusStore {
    const val SENDING = 0
    const val SENT = 1
    const val DELIVERED = 2
    const val FAILED = 3

    private const val SP = "msg_status"
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(SP, Context.MODE_PRIVATE)

    fun create(ctx: Context, sendId: String, address: String, body: String, totalParts: Int) {
        prune(ctx)
        val o = JSONObject()
            .put("address", address)
            .put("body", body)
            .put("time", System.currentTimeMillis())
            .put("total", totalParts)
            .put("sent", "0".repeat(totalParts))
            .put("delivered", "0".repeat(totalParts))
            .put("failed", false)
        sp(ctx).edit().putString(sendId, o.toString()).apply()
    }

    fun markSent(ctx: Context, sendId: String, part: Int) =
        setPart(ctx, sendId, part, "sent")

    fun markDelivered(ctx: Context, sendId: String, part: Int) =
        setPart(ctx, sendId, part, "delivered")

    fun markFailed(ctx: Context, sendId: String, part: Int) {
        try {
            val s = sp(ctx).getString(sendId, null) ?: return
            val o = JSONObject(s).put("failed", true)
            // A failed part can never become sent/delivered; mark sent-bit so it
            // doesn't linger in SENDING state.
            setBit(o, "sent", part)
            sp(ctx).edit().putString(sendId, o.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun setPart(ctx: Context, sendId: String, part: Int, field: String) {
        try {
            val s = sp(ctx).getString(sendId, null) ?: return
            val o = JSONObject(s)
            setBit(o, field, part)
            sp(ctx).edit().putString(sendId, o.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun setBit(o: JSONObject, field: String, part: Int) {
        val cur = o.optString(field, "")
        if (part in cur.indices) {
            val sb = StringBuilder(cur)
            sb.setCharAt(part, '1')
            o.put(field, sb.toString())
        }
    }

    /** Best-effort match of a provider message to a tracked send. Null = unknown. */
    fun stateFor(ctx: Context, address: String, body: String, date: Long): Int? {
        var best: Pair<Long, Int>? = null
        for ((_, v) in sp(ctx).all) {
            if (v !is String) continue
            try {
                val o = JSONObject(v)
                if (o.optString("address") != address) continue
                if (o.optString("body") != body) continue
                val t = o.optLong("time", 0)
                if (date < t - 60_000 || date > t + 10 * 60_000) continue
                if (best == null || t > best.first) best = t to stateOf(o)
            } catch (_: Exception) {}
        }
        return best?.second
    }

    private fun stateOf(o: JSONObject): Int {
        if (o.optBoolean("failed", false)) return FAILED
        val total = o.optInt("total", 1).coerceAtLeast(1)
        val sent = o.optString("delivered", "").count { it == '1' }
        if (sent >= total) return DELIVERED
        val sentOk = o.optString("sent", "").count { it == '1' }
        if (sentOk >= total) return SENT
        return SENDING
    }

    private fun prune(ctx: Context) {
        try {
            val now = System.currentTimeMillis()
            val ed = sp(ctx).edit()
            var changed = false
            for ((k, v) in sp(ctx).all) {
                if (v !is String) continue
                try {
                    if (now - JSONObject(v).optLong("time", now) > MAX_AGE_MS) {
                        ed.remove(k)
                        changed = true
                    }
                } catch (_: Exception) {
                    ed.remove(k)
                    changed = true
                }
            }
            if (changed) ed.apply()
        } catch (_: Exception) {}
    }
}
