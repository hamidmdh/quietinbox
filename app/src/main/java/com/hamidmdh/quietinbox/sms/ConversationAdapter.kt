package com.hamidmdh.quietinbox.sms

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hamidmdh.quietinbox.sms.data.Conversation
import com.hamidmdh.quietinbox.sms.databinding.ItemConversationBinding
import com.hamidmdh.quietinbox.sms.util.AvatarHelper
import com.hamidmdh.quietinbox.sms.util.TimeUtils

class ConversationAdapter(
    private var items: List<Conversation>,
    private val onClick: (Conversation) -> Unit,
    private val onLongClick: (Conversation) -> Unit = {}
) : RecyclerView.Adapter<ConversationAdapter.VH>() {

    class VH(val b: ItemConversationBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(b)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val c = items[position]
        val ctx = h.itemView.context
        h.b.tvAddress.text = c.displayName.ifBlank { c.address }
        h.b.tvSnippet.text = c.snippet
        h.b.tvDate.text = TimeUtils.friendly(ctx, c.date)
        AvatarHelper.bind(h.b.avatarPhoto, h.b.avatarText, c.displayName, c.address, c.photoUri)
        h.b.unreadDot.visibility = if (c.unreadCount > 0) View.VISIBLE else View.GONE
        h.b.tvAddress.setTypeface(
            null,
            if (c.unreadCount > 0) Typeface.BOLD else Typeface.NORMAL
        )
        h.itemView.setOnClickListener { onClick(c) }
        h.itemView.setOnLongClickListener { onLongClick(c); true }
    }

    override fun getItemCount() = items.size

    fun submit(newItems: List<Conversation>) {
        items = newItems
        notifyDataSetChanged()
    }
}
