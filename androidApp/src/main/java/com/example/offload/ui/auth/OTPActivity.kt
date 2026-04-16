package com.example.offload.ui.auth
import com.example.offload.R

import com.example.offload.ui.auth.*
import com.example.offload.ui.main.*
import com.example.offload.ui.adapter.*
import com.example.offload.viewmodel.*
import com.example.offload.model.*
import com.example.offload.data.local.*
import com.example.offload.data.remote.*
import com.example.offload.engine.*



import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class OTPActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_otpactivity)

        // 1. Link the Verify button
        val btnVerify = findViewById<Button>(R.id.btnVerify)

        btnVerify.setOnClickListener {
            // Save registration status
            val prefs = getSharedPreferences("OffloadXPrefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_registered", true).apply()

            // In a skeleton, we simulate success
            Toast.makeText(this, "Account Verified Successfully!", Toast.LENGTH_SHORT).show()

            // Go back to Login
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP // Clears previous screens
            startActivity(intent)
            finish()
        }

        // 2. Fix the edge-to-edge padding (Option B)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}