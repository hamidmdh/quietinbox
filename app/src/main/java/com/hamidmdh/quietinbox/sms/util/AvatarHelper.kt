package com.hamidmdh.quietinbox.sms.util

import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView

object AvatarHelper {
    private val COLORS = intArrayOf(
        0xFF5C6BC0.toInt(),
        0xFF26A69A.toInt(),
        0xFFEC407A.toInt(),
        0xFFEF6C00.toInt(),
        0xFF7E57C2.toInt(),
        0xFF0288D1.toInt(),
        0xFF689F38.toInt(),
        0xFFD81B60.toInt()
    )

    fun colorFor(key: String): Int =
        COLORS[(key.hashCode() and 0x7fffffff) % COLORS.size]

    fun initial(name: String): String {
        val t = name.trim()
        if (t.isEmpty()) return "#"
        val first = t.first()
        if (first == '+') {
            val digit = t.drop(1).firstOrNull { it.isDigit() }
            if (digit != null) return digit.uppercase()
        }
        return first.uppercase()
    }

    /** Shows the contact photo when available, otherwise a colored initial. */
    fun bind(
        photo: ImageView,
        fallback: TextView,
        displayName: String,
        address: String,
        photoUri: String?
    ) {
        if (!photoUri.isNullOrBlank()) {
            try {
                photo.setImageURI(Uri.parse(photoUri))
                photo.visibility = View.VISIBLE
                fallback.visibility = View.GONE
                return
            } catch (_: Exception) {
            } catch (_: OutOfMemoryError) {
            }
        }
        photo.visibility = View.GONE
        fallback.visibility = View.VISIBLE
        fallback.text = initial(if (displayName.isBlank()) address else displayName)
        fallback.backgroundTintList = ColorStateList.valueOf(colorFor(address))
    }

    /**
     * List-safe variant: paints the initial immediately and decodes the photo
     * off the UI thread, so fast scrolling never janks on disk I/O.
     */
    fun bindAsync(
        photo: ImageView,
        fallback: TextView,
        displayName: String,
        address: String,
        photoUri: String?
    ) {
        if (photoUri.isNullOrBlank()) {
            photo.tag = null
            bind(photo, fallback, displayName, address, null)
            return
        }
        bind(photo, fallback, displayName, address, null)
        photo.tag = photoUri
        Thread {
            try {
                val bmp = photo.context.applicationContext.contentResolver
                    .openInputStream(Uri.parse(photoUri))?.use { BitmapFactory.decodeStream(it) }
                photo.post {
                    if (photo.tag == photoUri && bmp != null) {
                        photo.setImageBitmap(bmp)
                        photo.visibility = View.VISIBLE
                        fallback.visibility = View.GONE
                    }
                }
            } catch (_: Exception) {
            } catch (_: OutOfMemoryError) {
            }
        }.start()
    }
}
