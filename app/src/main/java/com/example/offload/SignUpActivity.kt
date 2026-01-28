package com.example.offload

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SignUpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_up)

        // 1. Link the views once (Ensure these IDs match your activity_sign_up.xml)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val etEmail = findViewById<EditText>(R.id.etEmail)

        // 2. Set the click listener for the Register button
        btnRegister.setOnClickListener {
            val email = etEmail.text.toString()
            if (email.isNotEmpty()) {
                // Navigate to OTP Activity
                val intent = Intent(this, OTPActivity::class.java)
                intent.putExtra("USER_EMAIL", email)
                startActivity(intent)
            } else {
                etEmail.error = "Please enter an email"
            }
        }

        // 3. Handle Edge-to-Edge padding (Ensures R.id.main is used)
        val mainLayout = findViewById<android.view.View>(R.id.main)
        if (mainLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }
    }
}