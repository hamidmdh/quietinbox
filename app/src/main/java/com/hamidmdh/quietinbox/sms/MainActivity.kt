package com.hamidmdh.quietinbox.sms

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.view.Menu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

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
            R.id.action_default -> {
                promptDefaultSms()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun promptDefaultSms() {
        if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
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
