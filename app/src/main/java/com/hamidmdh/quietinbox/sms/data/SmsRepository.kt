package com.hamidmdh.quietinbox.sms.data

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony

object SmsRepository {

    private class Agg(
        var address: String,
        var body: String,
        var date: Long,
        var unread: Int
    )

    fun loadConversations(context: Context, prefs: Prefs): List<Conversation> {
        val map = LinkedHashMap<Long, Agg>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.READ
                ),
                null, null,
                Telephony.Sms.DATE + " DESC"
            )?.use { c ->
                val iThread = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val iType = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val iRead = c.getColumnIndexOrThrow(Telephony.Sms.READ)
                while (c.moveToNext()) {
                    val threadId = c.getLong(iThread)
                    val isUnread = c.getInt(iType) == Telephony.Sms.MESSAGE_TYPE_INBOX &&
                        c.getInt(iRead) == 0
                    val existing = map[threadId]
                    if (existing == null) {
                        map[threadId] = Agg(
                            c.getString(iAddr).orEmpty(),
                            c.getString(iBody).orEmpty(),
                            c.getLong(iDate),
                            if (isUnread) 1 else 0
                        )
                    } else if (isUnread) {
                        existing.unread++
                    }
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
        return map.entries
            .sortedByDescending { it.value.date }
            .map { (threadId, a) ->
                val info = ContactUtils.getInfo(context, a.address)
                val name = if (info.isSaved && !info.name.isNullOrBlank()) {
                    info.name!!
                } else {
                    a.address.ifBlank { "Unknown" }
                }
                Conversation(
                    threadId = threadId,
                    address = a.address,
                    displayName = name,
                    photoUri = info.photoUri,
                    snippet = a.body,
                    date = a.date,
                    isSavedContact = info.isSaved,
                    unreadCount = a.unread,
                    blocked = prefs.isBlocked(a.address)
                )
            }
    }

    fun loadMessages(context: Context, threadId: Long): List<Message> {
        val out = mutableListOf<Message>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID, Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                    Telephony.Sms.DATE, Telephony.Sms.TYPE
                ),
                Telephony.Sms.THREAD_ID + " = ?",
                arrayOf(threadId.toString()),
                Telephony.Sms.DATE + " ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    out.add(
                        Message(
                            id = c.getLong(0),
                            threadId = c.getLong(1),
                            address = c.getString(2).orEmpty(),
                            body = c.getString(3).orEmpty(),
                            date = c.getLong(4),
                            type = c.getInt(5)
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return out
    }

    fun markThreadRead(context: Context, threadId: Long) {
        try {
            val v = ContentValues().apply { put(Telephony.Sms.READ, 1) }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI, v,
                "thread_id = ? AND read = 0",
                arrayOf(threadId.toString())
            )
        } catch (_: Exception) {}
    }

    fun threadIdFor(context: Context, address: String): Long {
        return try {
            Telephony.Threads.getOrCreateThreadId(context, address)
        } catch (_: Exception) { 0L }
    }

    /**
     * The default SMS app must persist incoming messages itself:
     * the system does NOT write SMS_DELIVER messages to the provider.
     */
    fun insertInbox(context: Context, address: String, body: String, date: Long = System.currentTimeMillis()) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, date)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                put(Telephony.Sms.READ, 0)
            }
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        } catch (_: Exception) {}
    }

    /**
     * Persist an outgoing message, but only if the system hasn't already
     * auto-inserted it (behavior varies by OEM/MIUI), to avoid duplicates.
     */
    fun insertSentIfMissing(context: Context, address: String, body: String) {
        try {
            val since = System.currentTimeMillis() - 120_000
            context.contentResolver.query(
                Telephony.Sms.Sent.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                Telephony.Sms.ADDRESS + " = ? AND " + Telephony.Sms.BODY + " = ? AND " +
                    Telephony.Sms.DATE + " > ?",
                arrayOf(address, body, since.toString()),
                null
            )?.use { c -> if (c.moveToFirst()) return }
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.READ, 1)
            }
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        } catch (_: Exception) {}
    }
}
