package com.example.offload

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        // 1. Link the XML "Create Account" text to a variable
        val signUpLink = findViewById<TextView>(R.id.tvSignUp)

// 2. Tell the app what to do when it's clicked
        signUpLink.setOnClickListener {
            // This "Intent" tells the app to move from Login to SignUp
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }

// 3. Link the "Forgot Credentials" text
        val forgotLink = findViewById<TextView>(R.id.tvForgot)
        forgotLink.setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
        }
        // Link the Sign In button
        val btnLogin = findViewById<android.widget.Button>(R.id.btnLogin)
        btnLogin.setOnClickListener {
            // For the skeleton, we just navigate to the Main Activity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // Closes Login so you can't "Go Back" to it
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}