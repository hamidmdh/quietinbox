package com.hamidmdh.quietinbox.sms.data

import android.content.Context

/**
 * Shared single-flight cache for the conversation list.
 * All three tabs (and the blocked screen) read from one load instead of
 * each doing a full provider query + per-thread contact lookup, which is
 * what made startup take seconds. Fresh for [TTL_MS], invalidated on
 * any data change (receive / delete / block).
 */
object ConversationCache {
    private const val TTL_MS = 5_000L

    private var data: List<Conversation>? = null
    private var ts = 0L
    private var loading = false
    private val waiters = mutableListOf<(List<Conversation>) -> Unit>()

    @Synchronized
    fun invalidate() {
        data = null
        ts = 0L
    }

    fun loadAsync(context: Context, done: (List<Conversation>) -> Unit) {
        val app = context.applicationContext
        val fresh: List<Conversation>? = synchronized(this) {
            if (data != null && System.currentTimeMillis() - ts < TTL_MS) data else null
        }
        if (fresh != null) {
            done(fresh)
            return
        }
        synchronized(this) {
            if (loading) {
                waiters.add(done)
                return
            }
            loading = true
        }
        Thread {
            val list = try {
                SmsRepository.loadConversations(app, Prefs(app))
            } catch (_: Exception) {
                emptyList()
            }
            val pending: List<(List<Conversation>) -> Unit>
            synchronized(this) {
                data = list
                ts = System.currentTimeMillis()
                loading = false
                pending = waiters.toList()
                waiters.clear()
            }
            try {
                done(list)
            } catch (_: Exception) {}
            pending.forEach {
                try {
                    it(list)
                } catch (_: Exception) {}
            }
        }.start()
    }
}
