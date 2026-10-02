package com.hamidmdh.quietinbox.sms.util

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.hamidmdh.quietinbox.sms.ConversationActivity
import com.hamidmdh.quietinbox.sms.R

/**
 * Messenger-style floating chat heads. One bubble per unread conversation,
 * stacked vertically; each shows the contact avatar and an unread count.
 */
object BubbleManager {
    private data class Bubble(val view: View, var count: Int)

    private val bubbles = mutableMapOf<Long, Bubble>()
    private val handler = Handler(Looper.getMainLooper())

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun show(
        context: Context,
        threadId: Long,
        address: String,
        displayName: String,
        photoUri: String?
    ) {
        if (!hasPermission(context)) return
        val app = context.applicationContext
        val wm = app.getSystemService(WindowManager::class.java) ?: return
        bubbles[threadId]?.let {
            it.count++
            it.view.findViewById<TextView>(R.id.bubbleBadge)?.apply {
                text = it.count.toString()
                visibility = View.VISIBLE
            }
            return
        }
        try {
            val view = LayoutInflater.from(app).inflate(R.layout.bubble_layout, FrameLayout(app), false)
            AvatarHelper.bind(
                view.findViewById(R.id.bubbleAvatarPhoto),
                view.findViewById(R.id.bubbleAvatarText),
                displayName, address, photoUri
            )
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 16
                y = 220 + bubbles.size * 200
            }
            wm.addView(view, params)
            val bubble = Bubble(view, 1)
            bubbles[threadId] = bubble

            view.findViewById<View>(R.id.bubbleClose).setOnClickListener { dismiss(app, threadId) }
            attachDragTap(view, wm, params) {
                dismiss(app, threadId)
                app.startActivity(Intent(app, ConversationActivity::class.java).apply {
                    putExtra(ConversationActivity.EXTRA_THREAD_ID, threadId)
                    putExtra(ConversationActivity.EXTRA_ADDRESS, address)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
            // Auto-dismiss after 90s so stale bubbles never linger.
            handler.postDelayed({ dismiss(app, threadId) }, 90_000)
        } catch (_: Exception) {}
    }

    fun dismiss(context: Context, threadId: Long) {
        val bubble = bubbles.remove(threadId) ?: return
        try {
            context.applicationContext
                .getSystemService(WindowManager::class.java)
                ?.removeView(bubble.view)
        } catch (_: Exception) {}
    }

    fun dismissAll(context: Context) {
        for (id in bubbles.keys.toList()) dismiss(context, id)
    }

    private fun attachDragTap(
        view: View,
        wm: WindowManager,
        params: WindowManager.LayoutParams,
        onTap: () -> Unit
    ) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var downT = 0L
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = params.x
                    startY = params.y
                    downT = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX - (e.rawX - downX).toInt()
                    params.y = startY + (e.rawY - downY).toInt()
                    try {
                        wm.updateViewLayout(view, params)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = kotlin.math.abs(e.rawX - downX) +
                        kotlin.math.abs(e.rawY - downY)
                    if (moved < 20 && System.currentTimeMillis() - downT < 400) {
                        view.performClick()
                        onTap()
                    }
                    true
                }
                else -> false
            }
        }
    }
}
