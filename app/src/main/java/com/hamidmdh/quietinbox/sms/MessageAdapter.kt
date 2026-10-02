package com.hamidmdh.quietinbox.sms

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.hamidmdh.quietinbox.sms.data.Message
import com.hamidmdh.quietinbox.sms.databinding.ItemMessageBinding
import com.hamidmdh.quietinbox.sms.util.MessageStatusStore
import java.text.DateFormat
import java.util.Date

class MessageAdapter(
    private var items: List<Message>,
    private val statusFor: (Message) -> Int?,
    private val selected: Set<Long>,
    private val onClick: (Message) -> Unit,
    private val onLongClick: (Message) -> Unit
) : RecyclerView.Adapter<MessageAdapter.VH>() {

    class VH(val b: ItemMessageBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(b)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val m = items[position]
        val ctx = h.itemView.context
        h.b.tvBody.text = m.body
        h.b.tvDate.text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(m.date))
        val outgoing = m.type != 1
        if (outgoing) {
            h.b.spaceStart.visibility = View.VISIBLE
            h.b.spaceEnd.visibility = View.GONE
            h.b.card.setCardBackgroundColor(
                MaterialColors.getColor(h.b.card, com.google.android.material.R.attr.colorPrimary)
            )
            h.b.tvBody.setTextColor(
                MaterialColors.getColor(h.b.card, com.google.android.material.R.attr.colorOnPrimary)
            )
            h.b.metaRow.gravity = Gravity.END
            h.b.tvStatus.visibility = View.VISIBLE
            when (statusFor(m)) {
                MessageStatusStore.DELIVERED -> {
                    h.b.tvStatus.text = "✓✓"
                    h.b.tvStatus.setTextColor(
                        MaterialColors.getColor(h.b.card, com.google.android.material.R.attr.colorOnPrimary)
                    )
                }
                MessageStatusStore.FAILED -> {
                    h.b.tvStatus.text = "!"
                    h.b.tvStatus.setTextColor(0xFFFF8A80.toInt())
                }
                MessageStatusStore.SENDING -> {
                    h.b.tvStatus.text = "…"
                    h.b.tvStatus.setTextColor(
                        MaterialColors.getColor(h.b.card, com.google.android.material.R.attr.colorOnPrimary)
                    )
                }
                else -> { // SENT or unknown: in provider = accepted by radio
                    h.b.tvStatus.text = "✓"
                    h.b.tvStatus.setTextColor(
                        MaterialColors.getColor(h.b.card, com.google.android.material.R.attr.colorOnPrimary)
                    )
                }
            }
        } else {
            h.b.spaceStart.visibility = View.GONE
            h.b.spaceEnd.visibility = View.VISIBLE
            h.b.card.setCardBackgroundColor(
                MaterialColors.getColor(h.b.card, com.google.android.material.R.attr.colorSurfaceVariant)
            )
            h.b.tvBody.setTextColor(
                MaterialColors.getColor(h.b.card, com.google.android.material.R.attr.colorOnSurfaceVariant)
            )
            h.b.metaRow.gravity = Gravity.START
            h.b.tvStatus.visibility = View.GONE
        }
        val isSel = selected.contains(m.id)
        h.b.card.strokeWidth = if (isSel) 6 else 0
        h.b.card.strokeColor = MaterialColors.getColor(
            h.b.card, com.google.android.material.R.attr.colorTertiary
        )
        h.itemView.setOnClickListener { onClick(m) }
        h.itemView.setOnLongClickListener { onLongClick(m); true }
    }

    override fun getItemCount() = items.size

    fun submit(newItems: List<Message>) {
        items = newItems
        notifyDataSetChanged()
    }
}
