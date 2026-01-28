package com.example.offload

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ForgotPasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)

        // 1. Initialize your views (This fixes the red text)
        // Ensure these IDs match your activity_forgot_password.xml exactly!
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val btnReset = findViewById<Button>(R.id.btnReset)

        // 2. Set the click listener
        btnReset.setOnClickListener {
            val email = etEmail.text.toString()
            if (email.isNotEmpty()) {
                // Generate a random 6-character password for the wireframe
                val randomPass = (100000..999999).random().toString()

                // Show a Toast simulating the email being sent
                Toast.makeText(this, "Credentials sent to $email\nTemp Pass: $randomPass", Toast.LENGTH_LONG).show()

                // Return to Login Screen after 5 seconds
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    finish()
                }, 5000)
            } else {
                etEmail.error = "Please enter your registered email"
            }
        }

        // Apply window insets (Ensures R.id.main exists in activity_forgot_password.xml)
        val mainView = findViewById<android.view.View>(R.id.main)
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }
    }
}