package com.hamidmdh.quietinbox.sms

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hamidmdh.quietinbox.sms.data.SmsRepository
import com.hamidmdh.quietinbox.sms.databinding.ActivityComposeBinding
import com.hamidmdh.quietinbox.sms.util.SmsSender

class ComposeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComposeBinding

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
