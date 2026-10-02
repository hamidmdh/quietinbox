package com.hamidmdh.quietinbox.sms

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.hamidmdh.quietinbox.sms.data.Prefs
import com.hamidmdh.quietinbox.sms.data.SmsRepository
import com.hamidmdh.quietinbox.sms.databinding.ActivityBlockedBinding

class BlockedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockedBinding
    private lateinit var adapter: ConversationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.blocked)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = ConversationAdapter(
            emptyList(),
            onClick = { conv ->
                startActivity(Intent(this, ConversationActivity::class.java).apply {
                    putExtra(ConversationActivity.EXTRA_THREAD_ID, conv.threadId)
                    putExtra(ConversationActivity.EXTRA_ADDRESS, conv.address)
                })
            },
            onLongClick = { conv -> confirmUnblock(conv.displayName, conv.address, conv.threadId) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        Thread {
            val prefs = Prefs(this)
            val blocked = SmsRepository.loadConversations(this, prefs)
                .filter { it.blocked }
            runOnUiThread {
                adapter.submit(blocked)
                binding.empty.visibility = if (blocked.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    private fun confirmUnblock(name: String, address: String, threadId: Long) {
        val options = arrayOf(
            getString(R.string.delete_conversation),
            getString(R.string.unblock)
        )
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> AlertDialog.Builder(this)
                        .setMessage(getString(R.string.delete_conversation_confirm))
                        .setPositiveButton(getString(R.string.delete)) { _, _ ->
                            Thread {
                                SmsRepository.deleteThread(this, threadId)
                                runOnUiThread { load() }
                            }.start()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    1 -> AlertDialog.Builder(this)
                        .setMessage(getString(R.string.unblock_confirm))
                        .setPositiveButton(getString(R.string.unblock)) { _, _ ->
                            Prefs(this).setBlocked(address, false)
                            load()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
            .show()
    }
}
