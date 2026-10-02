package com.quietinbox.sms.data

import android.content.Context
import android.net.Uri
import android.provider.Telephony

object SmsRepository {

    fun loadConversations(context: Context): List<Conversation> {
        val list = mutableListOf<Conversation>()
        try {
            val uri: Uri = Telephony.Sms.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            )
            context.contentResolver.query(
                uri, projection, null, null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { c ->
                val seen = mutableSetOf<Long>()
                val iThread = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (c.moveToNext()) {
                    val threadId = c.getLong(iThread)
                    if (!seen.add(threadId)) continue // keep latest msg per thread
                    val address = c.getString(iAddr).orEmpty()
                    val body = c.getString(iBody).orEmpty()
                    val date = c.getLong(iDate)
                    val saved = ContactUtils.isSavedNumber(context, address)
                    list.add(Conversation(threadId, address, body, date, saved))
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
        return list
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
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} ASC"
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

    fun threadIdFor(context: Context, address: String): Long {
        // Telephony.Threads.getOrCreateThreadId is the canonical way.
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
            val values = android.content.ContentValues().apply {
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
                "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.BODY} = ? AND ${Telephony.Sms.DATE} > ?",
                arrayOf(address, body, since.toString()),
                null
            )?.use { c -> if (c.moveToFirst()) return }
            val values = android.content.ContentValues().apply {
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
