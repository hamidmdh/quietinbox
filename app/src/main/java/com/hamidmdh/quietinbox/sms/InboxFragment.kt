package com.hamidmdh.quietinbox.sms

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import android.content.Intent
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hamidmdh.quietinbox.sms.data.Prefs
import com.hamidmdh.quietinbox.sms.data.SmsRepository

class InboxFragment : Fragment() {

    private var tab: Int = 2
    private lateinit var adapter: ConversationAdapter
    private var recycler: RecyclerView? = null
    private var empty: TextView? = null

    companion object {
        fun newInstance(tab: Int) = InboxFragment().apply {
            arguments = Bundle().apply { putInt("tab", tab) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab = arguments?.getInt("tab", 2) ?: 2
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_inbox, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recycler = view.findViewById(R.id.recycler)
        empty = view.findViewById(R.id.empty)
        adapter = ConversationAdapter(emptyList()) { conv ->
            startActivity(Intent(requireContext(), ConversationActivity::class.java).apply {
                putExtra(ConversationActivity.EXTRA_THREAD_ID, conv.threadId)
                putExtra(ConversationActivity.EXTRA_ADDRESS, conv.address)
            })
        }
        recycler?.layoutManager = LinearLayoutManager(requireContext())
        recycler?.adapter = adapter
        refresh()
    }

    fun refresh() {
        val ctx = context ?: return
        Thread {
            val prefs = Prefs(ctx)
            val all = SmsRepository.loadConversations(ctx)
            val filtered = when (tab) {
                0 -> if (prefs.filterEnabled) all.filter { it.isSavedContact } else all
                1 -> if (prefs.filterEnabled) all.filter { !it.isSavedContact } else emptyList()
                else -> all
            }
            activity?.runOnUiThread {
                adapter.submit(filtered)
                empty?.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }
}
