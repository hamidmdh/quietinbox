package com.hamidmdh.quietinbox.sms.data

data class Conversation(
    val threadId: Long,
    val address: String,
    val displayName: String,
    val photoUri: String?,
    val snippet: String,
    val date: Long,
    val isSavedContact: Boolean,
    val unreadCount: Int,
    val blocked: Boolean
)

data class Message(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int // 1 = inbox, others = sent
)
