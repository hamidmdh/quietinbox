package com.quietinbox.sms

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.quietinbox.sms.data.ContactUtils
import com.quietinbox.sms.data.SmsRepository
import com.quietinbox.sms.databinding.ActivityConversationBinding
import com.quietinbox.sms.util.SmsSender

class ConversationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationBinding
    private lateinit var adapter: MessageAdapter
    private var threadId: Long = 0
    private var address: String = ""

    companion object {
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_ADDRESS = "address"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        threadId = intent.getLongExtra(EXTRA_THREAD_ID, 0)
        address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
        if (threadId == 0L && address.isNotEmpty()) {
            threadId = SmsRepository.threadIdFor(this, address)
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ContactUtils.displayName(this, address)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = MessageAdapter(emptyList())
        binding.recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.recycler.adapter = adapter

        binding.btnSend.setOnClickListener { sendCurrent() }
        load()
    }

    private fun load() {
        Thread {
            val msgs = SmsRepository.loadMessages(this, threadId)
            runOnUiThread {
                adapter.submit(msgs)
                if (msgs.isNotEmpty()) binding.recycler.scrollToPosition(msgs.size - 1)
            }
        }.start()
    }

    private fun sendCurrent() {
        val text = binding.input.text.toString()
        if (text.isBlank() || address.isBlank()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "SEND_SMS permission needed", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            SmsSender.send(this, address, text)
            SmsRepository.insertSentIfMissing(this, address, text)
            binding.input.text.clear()
            // Reload after a short delay so the sent SMS lands in the provider.
            binding.recycler.postDelayed({ load() }, 800)
        } catch (e: Exception) {
            Toast.makeText(this, "Send failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
