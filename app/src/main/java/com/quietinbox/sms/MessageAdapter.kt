package com.quietinbox.sms

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.quietinbox.sms.data.Message
import java.text.DateFormat
import java.util.Date

class MessageAdapter(private var items: List<Message>) :
    RecyclerView.Adapter<MessageAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvBody: TextView = v.findViewById(R.id.tvBody)
        val tvDate: TextView = v.findViewById(R.id.tvDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val m = items[position]
        h.tvBody.text = m.body
        h.tvDate.text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(m.date))
        // Outgoing (sent, type 2/4/5/6) on the right, incoming on the left.
        val outgoing = m.type != 1
        if (outgoing) {
            h.tvBody.setBackgroundColor(0xFFBBDEFB.toInt())
            (h.itemView as LinearLayout).gravity = Gravity.END
        } else {
            h.tvBody.setBackgroundColor(0xFFE0E0E0.toInt())
            (h.itemView as LinearLayout).gravity = Gravity.START
        }
    }

    override fun getItemCount() = items.size

    fun submit(newItems: List<Message>) {
        items = newItems
        notifyDataSetChanged()
    }
}
