package com.quietinbox.sms.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

object ContactUtils {
    private val cache = mutableMapOf<String, Boolean>()

    fun isSavedNumber(context: Context, rawAddress: String?): Boolean {
        if (rawAddress.isNullOrBlank()) return false
        // Short codes / alphanumeric senders (banks, OTPs) are treated as unknown.
        val digits = rawAddress.filter { it.isDigit() }
        if (digits.length < 7) return false
        cache[rawAddress]?.let { return it }
        val result = queryPhoneLookup(context, rawAddress)
        // Cache only positives aggressively; negatives cached briefly to avoid repeated queries.
        cache[rawAddress] = result
        if (cache.size > 500) cache.clear()
        return result
    }

    private fun queryPhoneLookup(context: Context, address: String): Boolean {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null, null, null
            )?.use { c -> c.moveToFirst() } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun clearCache() { cache.clear() }

    fun displayName(context: Context, address: String?): String {
        if (address.isNullOrBlank()) return "Unknown"
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0)
                    if (!name.isNullOrBlank()) return name
                }
            }
        } catch (_: Exception) {}
        return address
    }
}
