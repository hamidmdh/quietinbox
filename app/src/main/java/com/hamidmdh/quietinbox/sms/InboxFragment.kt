package com.hamidmdh.quietinbox.sms

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.hamidmdh.quietinbox.sms.data.Conversation
import com.hamidmdh.quietinbox.sms.data.Prefs
import com.hamidmdh.quietinbox.sms.data.SmsRepository
import com.hamidmdh.quietinbox.sms.databinding.FragmentInboxBinding

class InboxFragment : Fragment() {

    private var tab: Int = 2
    private var query: String = ""
    private var full: List<Conversation> = emptyList()
    private lateinit var adapter: ConversationAdapter
    private var binding: FragmentInboxBinding? = null

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
    ): View {
        val b = FragmentInboxBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ConversationAdapter(
            emptyList(),
            onClick = { conv ->
                startActivity(Intent(requireContext(), ConversationActivity::class.java).apply {
                    putExtra(ConversationActivity.EXTRA_THREAD_ID, conv.threadId)
                    putExtra(ConversationActivity.EXTRA_ADDRESS, conv.address)
                })
            },
            onLongClick = { conv -> confirmBlock(conv) }
        )
        binding?.recycler?.layoutManager = LinearLayoutManager(requireContext())
        binding?.recycler?.adapter = adapter
        applyFilter()
        refresh()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    fun setQuery(q: String) {
        query = q
        applyFilter()
    }

    fun refresh() {
        val ctx = context ?: return
        Thread {
            val prefs = Prefs(ctx)
            full = SmsRepository.loadConversations(ctx, prefs)
            activity?.runOnUiThread { applyFilter() }
        }.start()
    }

    private fun applyFilter() {
        val ctx = context
        val prefs = ctx?.let { Prefs(it) }
        val filterOn = prefs?.filterEnabled ?: true
        val q = query.trim()
        val list = full.filter { c ->
            if (c.blocked) return@filter false
            val tabOk = when (tab) {
                0 -> if (filterOn) c.isSavedContact else true
                1 -> if (filterOn) !c.isSavedContact else false
                else -> true
            }
            if (!tabOk) return@filter false
            if (q.isEmpty()) return@filter true
            c.displayName.contains(q, ignoreCase = true) ||
                c.address.contains(q, ignoreCase = true) ||
                c.snippet.contains(q, ignoreCase = true)
        }
        adapter.submit(list)
        binding?.empty?.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmBlock(c: Conversation) {
        val options = arrayOf(
            getString(R.string.delete_conversation),
            getString(R.string.block)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(c.displayName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> confirmDeleteConversation(c)
                    1 -> {
                        Prefs(requireContext()).setBlocked(c.address, true)
                        refresh()
                    }
                }
            }
            .show()
    }

    private fun confirmDeleteConversation(c: Conversation) {
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.delete_conversation_confirm))
            .setPositiveButton(R.string.delete) { _, _ ->
                Thread {
                    SmsRepository.deleteThread(requireContext(), c.threadId)
                    activity?.runOnUiThread { refresh() }
                }.start()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
