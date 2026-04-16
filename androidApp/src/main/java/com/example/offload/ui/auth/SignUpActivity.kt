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
import android.util.Patterns
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    // Modern Activity Result Launcher for Google Sign-In
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        auth = FirebaseAuth.getInstance()

        // --- Setup Google Sign-In ---
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val etUsername    = findViewById<TextInputEditText>(R.id.etNewUser)
        val etEmail       = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword    = findViewById<TextInputEditText>(R.id.etNewPass)
        val etConfirmPass = findViewById<TextInputEditText>(R.id.etConfirmPass)
        val btnRegister   = findViewById<Button>(R.id.btnRegister)
        val btnGoogleUp   = findViewById<Button>(R.id.btnGoogleSignUp)

        // --- Google Sign-Up Button ---
        btnGoogleUp.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        btnRegister.setOnClickListener {
            val username    = etUsername.text.toString().trim()
            val email       = etEmail.text.toString().trim()
            val password    = etPassword.text.toString()
            val confirmPass = etConfirmPass.text.toString()

            // --- VALIDATION CHECKS ---

            // 1. Empty fields
            if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPass.isEmpty()) {
                Toast.makeText(this, "All fields are required.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. Username length
            if (username.length < 3) {
                etUsername.error = "Username must be at least 3 characters"
                return@setOnClickListener
            }

            // 3. Email format
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Enter a valid email address"
                return@setOnClickListener
            }

            // 4. Password strength
            if (password.length < 6) {
                etPassword.error = "Password must be at least 6 characters"
                return@setOnClickListener
            }

            // 5. Password has at least one digit
            if (!password.any { it.isDigit() }) {
                etPassword.error = "Password must contain at least one number"
                return@setOnClickListener
            }

            // 6. Confirm password match
            if (password != confirmPass) {
                etConfirmPass.error = "Passwords do not match"
                return@setOnClickListener
            }

            // --- FIREBASE: Create Account ---
            btnRegister.isEnabled = false
            btnRegister.text = "Creating Account..."

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Send email verification
                        val user = auth.currentUser
                        user?.sendEmailVerification()
                            ?.addOnCompleteListener { verifyTask ->
                                if (verifyTask.isSuccessful) {
                                    // Save username locally
                                    val prefs = getSharedPreferences("OffloadXPrefs", MODE_PRIVATE)
                                    prefs.edit()
                                        .putBoolean("is_registered", true)
                                        .putString("saved_username", username)
                                        .putString("saved_email", email)
                                        .apply()

                                    Toast.makeText(this,
                                        "Verification email sent to $email. Check your inbox!",
                                        Toast.LENGTH_LONG).show()

                                    // Firebase auto-logins on account creation. Sign out immediately
                                    auth.signOut()

                                    // Go back to Login
                                    val intent = Intent(this, LoginActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    startActivity(intent)
                                    finish()
                                } else {
                                    Toast.makeText(this,
                                        "Failed to send verification email.",
                                        Toast.LENGTH_SHORT).show()
                                }
                            }
                    } else {
                        // Registration failed
                        val errorMsg = task.exception?.message ?: "Registration failed"
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                    }
                    btnRegister.isEnabled = true
                    btnRegister.text = "Create Account"
                }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Account Created/Signed In!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }
}