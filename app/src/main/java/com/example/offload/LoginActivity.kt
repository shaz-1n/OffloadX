package com.example.offload

import android.content.Intent
import android.os.Bundle
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
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

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
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // --- Auto-login: If already signed in, skip login screen ---
        val currentUser = auth.currentUser
        if (currentUser != null) {
            if (currentUser.isEmailVerified) {
                goToMain()
                return
            } else {
                // If they are cached but unverified, sign them out so they must login again.
                auth.signOut()
            }
        }

        // --- Setup Google Sign-In ---
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // --- Link Views ---
        val tilUsername = findViewById<TextInputLayout>(R.id.tilUsername)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val etUsername  = findViewById<TextInputEditText>(R.id.etUsername)
        val etPassword  = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin    = findViewById<Button>(R.id.btnLogin)
        val btnGoogle   = findViewById<Button>(R.id.btnGoogleSignIn)
        val tvSignUp    = findViewById<android.widget.TextView>(R.id.tvSignUp)
        val tvForgot    = findViewById<android.widget.TextView>(R.id.tvForgot)

        // --- Navigation links ---
        tvSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
        tvForgot.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        // --- Google Sign-In Button ---
        btnGoogle.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        // --- Email/Password Sign In Button ---
        btnLogin.setOnClickListener {
            // Clear previous errors
            tilUsername.error = null
            tilPassword.error = null

            val email    = etUsername.text.toString().trim()
            val password = etPassword.text.toString()

            // 1. Empty field checks
            if (email.isEmpty()) {
                tilUsername.error = "Email is required"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                tilPassword.error = "Password is required"
                return@setOnClickListener
            }

            // 2. Firebase Sign In
            btnLogin.isEnabled = false
            btnLogin.text = "Signing In..."

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser

                        // 3. Check if email is verified
                        if (user != null && user.isEmailVerified) {
                            Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()
                            goToMain()
                        } else {
                            Toast.makeText(this,
                                "Please verify your email first. Check your inbox.",
                                Toast.LENGTH_LONG).show()
                            auth.signOut()
                        }
                    } else {
                        val errorMsg = task.exception?.message ?: "Login failed"
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                    }
                    btnLogin.isEnabled = true
                    btnLogin.text = "Sign In"
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
                    Toast.makeText(this, "Welcome, ${auth.currentUser?.displayName}!", Toast.LENGTH_SHORT).show()
                    goToMain()
                } else {
                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}