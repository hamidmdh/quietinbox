package com.hamidmdh.quietinbox.sms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.hamidmdh.quietinbox.sms.data.ContactUtils
import com.hamidmdh.quietinbox.sms.data.ConversationCache
import com.hamidmdh.quietinbox.sms.data.Message
import com.hamidmdh.quietinbox.sms.data.Prefs
import com.hamidmdh.quietinbox.sms.data.SmsRepository
import com.hamidmdh.quietinbox.sms.databinding.ActivityConversationBinding
import com.hamidmdh.quietinbox.sms.util.AvatarHelper
import com.hamidmdh.quietinbox.sms.util.BubbleManager
import com.hamidmdh.quietinbox.sms.util.MessageStatusStore
import com.hamidmdh.quietinbox.sms.util.NotificationHelper
import com.hamidmdh.quietinbox.sms.util.SmsSender

class ConversationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationBinding
    private lateinit var adapter: MessageAdapter
    private var threadId: Long = 0
    private var address: String = ""
    private var messages: List<Message> = emptyList()
    private val selected = mutableSetOf<Long>()
    private var actionMode: ActionMode? = null

    companion object {
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_ADDRESS = "address"
    }

    private val ringtonePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            @Suppress("DEPRECATION")
            val uri: Uri? = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (res.resultCode == RESULT_OK && uri != null) {
                Prefs(this).setThreadSound(threadId, uri.toString())
                NotificationHelper.deleteThreadChannel(this, threadId)
                Toast.makeText(this, R.string.sound_saved, Toast.LENGTH_SHORT).show()
            }
        }

    private val audioFilePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                Prefs(this).setThreadSound(threadId, uri.toString())
                NotificationHelper.deleteThreadChannel(this, threadId)
                Toast.makeText(this, R.string.sound_saved, Toast.LENGTH_SHORT).show()
            }
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
        BubbleManager.dismiss(this, threadId)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Title = contact name (or raw sender ID), subtitle = raw sender always.
        val name = ContactUtils.displayName(this, address)
        binding.toolbarTitle.text = name.ifBlank { address }
        binding.toolbarSubtitle.text = address
        val info = ContactUtils.getInfo(this, address)
        AvatarHelper.bind(
            binding.toolbarAvatarPhoto, binding.toolbarAvatarText,
            name, address, info.photoUri
        )

        adapter = MessageAdapter(
            emptyList(),
            statusFor = { m ->
                if (m.type == 1) null
                else MessageStatusStore.stateFor(this, m.address, m.body, m.date)
            },
            selected = selected,
            onClick = { m -> if (actionMode != null) toggleSelect(m) },
            onLongClick = { m ->
                if (actionMode == null) startSelectMode(m) else toggleSelect(m)
            }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.recycler.adapter = adapter

        binding.btnSend.setOnClickListener { sendCurrent() }
        load()
        Thread { SmsRepository.markThreadRead(this, threadId) }.start()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.conv_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sound -> {
                showSoundDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSoundDialog() {
        val options = arrayOf(
            getString(R.string.sound_use_global),
            getString(R.string.sound_system_sound),
            getString(R.string.sound_audio_file),
            getString(R.string.sound_silent)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.notification_sound)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        Prefs(this).setThreadSound(threadId, null)
                        NotificationHelper.deleteThreadChannel(this, threadId)
                    }
                    1 -> openRingtonePicker()
                    2 -> try {
                        audioFilePicker.launch(arrayOf("audio/*"))
                    } catch (e: Exception) {
                        Toast.makeText(this, "No file picker: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        Prefs(this).setThreadSound(threadId, "NONE")
                        NotificationHelper.deleteThreadChannel(this, threadId)
                    }
                }
            }
            .show()
    }

    private fun openRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.notification_sound))
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI)
        }
        try {
            ringtonePicker.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No sound picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Selection + delete ----

    private fun startSelectMode(first: Message) {
        actionMode = startSupportActionMode(modeCallback)
        toggleSelect(first)
    }

    private fun toggleSelect(m: Message) {
        if (!selected.remove(m.id)) selected.add(m.id)
        if (selected.isEmpty()) {
            actionMode?.finish()
        } else {
            actionMode?.title = selected.size.toString()
            adapter.notifyDataSetChanged()
        }
    }

    private val modeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menuInflater.inflate(R.menu.select_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_delete -> {
                    confirmDeleteSelected()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            selected.clear()
            actionMode = null
            adapter.notifyDataSetChanged()
        }
    }

    private fun confirmDeleteSelected() {
        val n = selected.size
        if (n == 0) return
        val msg = if (n == 1) getString(R.string.delete_message_confirm)
        else getString(R.string.delete_messages_confirm, n)
        AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton(R.string.delete) { _, _ ->
                Thread {
                    SmsRepository.deleteMessages(this, selected.toList())
                    ConversationCache.invalidate()
                    runOnUiThread {
                        actionMode?.finish()
                        load()
                    }
                }.start()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun load() {
        Thread {
            val msgs = SmsRepository.loadMessages(this, threadId)
            runOnUiThread {
                messages = msgs
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
            // Refresh ticks as radio/delivery reports arrive.
            binding.recycler.postDelayed({ adapter.notifyDataSetChanged() }, 4000)
        } catch (e: Exception) {
            Toast.makeText(this, "Send failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
