package com.hamidmdh.quietinbox.sms.data

import android.content.Context
import android.provider.ContactsContract

data class ContactInfo(
    val name: String?,
    val photoUri: String?,
    val isSaved: Boolean
)

/**
 * Looks up ONLY the user's own contacts (exact number match).
 * Service IDs (letters, e.g. AD-BANK) and short codes are never treated
 * as contacts: they are shown exactly as received, no name is assigned.
 */
object ContactUtils {
    private val cache = mutableMapOf<String, ContactInfo>()

    fun getInfo(context: Context, rawAddress: String?): ContactInfo {
        if (rawAddress.isNullOrBlank()) return ContactInfo(null, null, false)
        cache[rawAddress]?.let { return it }
        val info = lookup(context, rawAddress)
        cache[rawAddress] = info
        if (cache.size > 500) cache.clear()
        return info
    }

    fun isSavedNumber(context: Context, rawAddress: String?) =
        getInfo(context, rawAddress).isSaved

    /** Saved contact name, otherwise the raw sender exactly as received. */
    fun displayName(context: Context, address: String?): String {
        if (address.isNullOrBlank()) return "Unknown"
        val info = getInfo(context, address)
        return if (info.isSaved && !info.name.isNullOrBlank()) info.name!! else address
    }

    private fun lookup(context: Context, address: String): ContactInfo {
        if (address.any { it.isLetter() }) return ContactInfo(null, null, false)
        val digits = address.filter { it.isDigit() }
        if (digits.length < 7) return ContactInfo(null, null, false)
        return try {
            val suffix = digits.takeLast(10)
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
                ),
                ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?",
                arrayOf("%$suffix"),
                null
            )?.use { c ->
                val iNum = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val iName = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val iPhoto = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
                while (c.moveToNext()) {
                    val numDigits = c.getString(iNum).orEmpty().filter { it.isDigit() }
                    if (numDigits.takeLast(10) == suffix) {
                        return ContactInfo(c.getString(iName), c.getString(iPhoto), true)
                    }
                }
                ContactInfo(null, null, false)
            } ?: ContactInfo(null, null, false)
        } catch (_: SecurityException) {
            ContactInfo(null, null, false)
        } catch (_: Exception) {
            ContactInfo(null, null, false)
        }
    }

    fun clearCache() { cache.clear() }
}
