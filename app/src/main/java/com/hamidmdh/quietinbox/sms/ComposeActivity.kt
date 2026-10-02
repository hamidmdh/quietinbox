package com.hamidmdh.quietinbox.sms

import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.hamidmdh.quietinbox.sms.data.SmsRepository
import com.hamidmdh.quietinbox.sms.databinding.ActivityComposeBinding
import com.hamidmdh.quietinbox.sms.util.AvatarHelper
import com.hamidmdh.quietinbox.sms.util.SmsSender

class ComposeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComposeBinding

    private val pickContact =
        registerForActivityResult(ActivityResultContracts.PickContact()) { uri ->
            if (uri == null) return@registerForActivityResult
            val contactId = uri.lastPathSegment ?: return@registerForActivityResult
            try {
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
                    ),
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf(contactId),
                    null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val number = c.getString(0).orEmpty()
                        val name = c.getString(1).orEmpty()
                        val photo = c.getString(2)
                        binding.toField.setText(number)
                        if (name.isNotBlank()) {
                            binding.pickedRow.visibility = View.VISIBLE
                            binding.tvContactName.text = name
                            AvatarHelper.bind(
                                binding.pickedAvatarPhoto, binding.pickedAvatarText,
                                name, number, photo
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot read contact: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComposeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle SENDTO: intent from other apps when we are the default SMS app.
        intent?.data?.schemeSpecificPart?.let { shared ->
            if (binding.toField.text.isNullOrBlank() && shared.isNotBlank()) {
                binding.toField.setText(shared)
            }
        }

        binding.btnPick.setOnClickListener {
            try {
                pickContact.launch(null)
            } catch (e: Exception) {
                Toast.makeText(this, "No contacts app found", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSend.setOnClickListener {
            val to = binding.toField.text.toString().trim()
            val text = binding.input.text.toString()
            if (to.isBlank() || text.isBlank()) {
                Toast.makeText(this, "Enter a number and a message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                SmsSender.send(this, to, text)
                SmsRepository.insertSentIfMissing(this, to, text)
                Toast.makeText(this, "Sent", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "Send failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
