package com.hamidmdh.quietinbox.sms

import android.Manifest
import android.app.role.RoleManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.view.Menu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.hamidmdh.quietinbox.sms.data.ContactUtils
import com.hamidmdh.quietinbox.sms.data.Prefs
import com.hamidmdh.quietinbox.sms.databinding.ActivityMainBinding
import com.hamidmdh.quietinbox.sms.util.BubbleManager
import com.hamidmdh.quietinbox.sms.util.NotificationHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

    private val ringtonePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            @Suppress("DEPRECATION")
            val uri: Uri? = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (res.resultCode == RESULT_OK && uri != null) {
                prefs.globalSound = uri.toString()
                clearAllThreadChannels()
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
                prefs.globalSound = uri.toString()
                clearAllThreadChannels()
                Toast.makeText(this, R.string.sound_saved, Toast.LENGTH_SHORT).show()
            }
        }

    private val overlayPermLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (!BubbleManager.hasPermission(this) && prefs.bubblesEnabled) {
                prefs.bubblesEnabled = false
                Toast.makeText(this, R.string.overlay_needed, Toast.LENGTH_LONG).show()
            }
            invalidateOptionsMenu()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        prefs = Prefs(this)

        binding.pager.adapter = TabsAdapter(this)
        TabLayoutMediator(binding.tabs, binding.pager) { tab, pos ->
            tab.text = when (pos) {
                0 -> getString(R.string.inbox)
                1 -> getString(R.string.unknown)
                else -> getString(R.string.all)
            }
        }.attach()

        binding.fabCompose.setOnClickListener {
            startActivity(Intent(this, ComposeActivity::class.java))
        }

        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        ContactUtils.clearCache()
        invalidateOptionsMenu()
        refresh()
    }

    private fun refresh() {
        (binding.pager.adapter as? TabsAdapter)?.refreshAll()
    }

    private fun checkPermissions() {
        val needed = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.action_filter)?.isChecked = prefs.filterEnabled
        menu.findItem(R.id.action_mute)?.isChecked = prefs.muteUnknown
        menu.findItem(R.id.action_bubbles)?.isChecked = prefs.bubblesEnabled
        val searchItem = menu.findItem(R.id.action_search)
        (searchItem?.actionView as? SearchView)?.apply {
            queryHint = getString(R.string.search)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(q: String?) = false
                override fun onQueryTextChange(q: String?): Boolean {
                    (binding.pager.adapter as? TabsAdapter)?.setQueryAll(q.orEmpty())
                    return true
                }
            })
        }
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_filter -> {
                prefs.filterEnabled = !prefs.filterEnabled
                item.isChecked = prefs.filterEnabled
                refresh()
                true
            }
            R.id.action_mute -> {
                prefs.muteUnknown = !prefs.muteUnknown
                item.isChecked = prefs.muteUnknown
                true
            }
            R.id.action_blocked -> {
                startActivity(Intent(this, BlockedActivity::class.java))
                true
            }
            R.id.action_sound -> {
                showGlobalSoundDialog()
                true
            }
            R.id.action_bubbles -> {
                prefs.bubblesEnabled = !prefs.bubblesEnabled
                item.isChecked = prefs.bubblesEnabled
                if (prefs.bubblesEnabled && !BubbleManager.hasPermission(this)) {
                    requestOverlayPermission()
                }
                if (!prefs.bubblesEnabled) BubbleManager.dismissAll(this)
                true
            }
            R.id.action_default -> {
                promptDefaultSms()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showGlobalSoundDialog() {
        val options = arrayOf(
            getString(R.string.sound_default),
            getString(R.string.sound_system_sound),
            getString(R.string.sound_audio_file)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.notification_sound)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        prefs.globalSound = null
                        clearAllThreadChannels()
                    }
                    1 -> openRingtonePicker()
                    2 -> try {
                        audioFilePicker.launch(arrayOf("audio/*"))
                    } catch (e: Exception) {
                        Toast.makeText(this, "No file picker: ${e.message}", Toast.LENGTH_SHORT).show()
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

    private fun clearAllThreadChannels() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(NotificationManager::class.java)
                nm.notificationChannels
                    .filter { it.id.startsWith("sms_t_") }
                    .forEach { nm.deleteNotificationChannel(it.id) }
            }
        } catch (_: Exception) {}
        prefs.clearAllChannelConfigs()
    }

    private fun requestOverlayPermission() {
        try {
            overlayPermLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open overlay settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun promptDefaultSms() {        if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
            Toast.makeText(this, "Already the default SMS app", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = getSystemService(RoleManager::class.java)
                startActivityForResult(
                    rm.createRequestRoleIntent(RoleManager.ROLE_SMS), 1234
                )
            } else {
                @Suppress("DEPRECATION")
                startActivity(Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                })
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open default-SMS settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private inner class TabsAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        private val fragments = mutableMapOf<Int, InboxFragment>()
        override fun getItemCount() = 3
        override fun createFragment(position: Int): Fragment {
            val f = InboxFragment.newInstance(position)
            fragments[position] = f
            return f
        }
        fun refreshAll() { fragments.values.forEach { it.refresh() } }
        fun setQueryAll(q: String) { fragments.values.forEach { it.setQuery(q) } }
    }
}
