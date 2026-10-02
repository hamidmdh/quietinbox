package com.hamidmdh.quietinbox.sms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hamidmdh.quietinbox.sms.data.ContactUtils
import com.hamidmdh.quietinbox.sms.data.Conversation
import java.text.DateFormat
import java.util.Date

class ConversationAdapter(
    private var items: List<Conversation>,
    private val onClick: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvAddress: TextView = v.findViewById(R.id.tvAddress)
        val tvSnippet: TextView = v.findViewById(R.id.tvSnippet)
        val tvDate: TextView = v.findViewById(R.id.tvDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val c = items[position]
        val ctx = h.itemView.context
        h.tvAddress.text = ContactUtils.displayName(ctx, c.address)
        h.tvSnippet.text = c.snippet
        h.tvDate.text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(c.date))
        h.itemView.setOnClickListener { onClick(c) }
    }

    override fun getItemCount() = items.size

    fun submit(newItems: List<Conversation>) {
        items = newItems
        notifyDataSetChanged()
    }
}
