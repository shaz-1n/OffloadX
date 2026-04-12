package com.example.offload

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.net.Uri
import android.widget.EditText
import android.widget.LinearLayout
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            view?.findViewById<ShapeableImageView>(R.id.ivProfilePic)?.setImageURI(uri)
            try {
                // Copy the picture to internal storage so we have persistent access
                val inputStream = requireActivity().contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val prefs = requireActivity().getSharedPreferences("OffloadXPrefs", android.content.Context.MODE_PRIVATE)
                    
                    // delete old file if it exists to clean up disk space
                    val oldUri = prefs.getString("pfp_uri", null)
                    if (oldUri != null && oldUri.startsWith("/")) {
                        java.io.File(oldUri).delete()
                    }
                
                    val fileName = "profile_pic_${System.currentTimeMillis()}.jpg"
                    val file = java.io.File(requireContext().filesDir, fileName)
                    val outputStream = java.io.FileOutputStream(file)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    
                    val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        view?.findViewById<ShapeableImageView>(R.id.ivProfilePic)?.setImageBitmap(bitmap)
                    }
                    
                    prefs.edit().putString("pfp_uri", file.absolutePath).apply()
                } else {
                    Toast.makeText(context, "Could not open selected image", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to save profile picture permanently", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvUserName  = view.findViewById<TextView>(R.id.tvUserName)
        val tvUserEmail   = view.findViewById<TextView>(R.id.tvUserEmail)
        val switch        = view.findViewById<SwitchCompat>(R.id.switchDarkMode)
        val btnPass       = view.findViewById<LinearLayout>(R.id.btnChangePasswordLayout)
        val btnLogout     = view.findViewById<Button>(R.id.btnLogout)
        val ivProfilePic  = view.findViewById<ShapeableImageView>(R.id.ivProfilePic)
        val btnEditProfile = view.findViewById<LinearLayout>(R.id.btnEditProfileLayout)
        val btnDeleteAccount = view.findViewById<Button>(R.id.btnDeleteAccount)
        
        // Edge Node components
        val btnSetHubIp = view.findViewById<LinearLayout>(R.id.btnSetHubIpLayout)
        val tvCurrentHubIp = view.findViewById<TextView>(R.id.tvCurrentHubIp)

        val prefs = requireActivity().getSharedPreferences("OffloadXPrefs", android.content.Context.MODE_PRIVATE)

        // --- Hub IP Configuration ---
        val currentIp = prefs.getString("hub_ip", "192.168.1.100:8000")
        tvCurrentHubIp.text = currentIp

        btnSetHubIp.setOnClickListener {
            val input = EditText(requireContext())
            input.setText(tvCurrentHubIp.text.toString())
            val container = LinearLayout(requireContext())
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(60, 20, 60, 20)
            input.layoutParams = params
            container.addView(input)

            AlertDialog.Builder(requireContext())
                .setTitle("Set Edge Node IP")
                .setMessage("Enter the IP address and port of your server (e.g., 192.168.42.129:8000)")
                .setView(container)
                .setPositiveButton("Save") { _, _ ->
                    val newIp = input.text.toString().trim()
                    if (newIp.isNotEmpty()) {
                        prefs.edit().putString("hub_ip", newIp).apply()
                        tvCurrentHubIp.text = newIp
                        Toast.makeText(context, "Hub IP updated!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // --- Profile Picture Logic ---
        val savedUri = prefs.getString("pfp_uri", null)
        if (savedUri != null) {
            try {
                if (savedUri.startsWith("/")) { // It's an internal file path
                    val bitmap = android.graphics.BitmapFactory.decodeFile(savedUri)
                    if (bitmap != null) {
                        ivProfilePic.setImageBitmap(bitmap)
                    } else {
                        ivProfilePic.setImageURI(Uri.fromFile(java.io.File(savedUri)))
                    }
                } else { // Fallback for old URI style
                    ivProfilePic.setImageURI(Uri.parse(savedUri))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        ivProfilePic.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        // --- Show REAL user data from Firebase ---
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            tvUserName.text  = user.displayName ?: "User"
            tvUserEmail.text = user.email ?: "No email"
        }

        // --- Dark Mode Toggle ---
        val isDark = prefs.getBoolean("dark_mode", AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)
        switch.isChecked = isDark

        switch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        // --- Change Password (Navigate to Fragment) ---
        btnPass.setOnClickListener {
            try {
                findNavController().navigate(R.id.changePasswordFragment)
            } catch (e: Exception) {
                Toast.makeText(context, "Navigation Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // --- Edit Profile Name ---
        btnEditProfile.setOnClickListener {
            val input = EditText(requireContext())
            input.setText(tvUserName.text.toString())
            val container = LinearLayout(requireContext())
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(60, 20, 60, 20)
            input.layoutParams = params
            container.addView(input)

            AlertDialog.Builder(requireContext())
                .setTitle("Edit Display Name")
                .setView(container)
                .setPositiveButton("Save") { _, _ ->
                    val newName = input.text.toString().trim()
                    if (newName.isNotEmpty() && user != null) {
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(newName)
                            .build()
                        user.updateProfile(profileUpdates).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                tvUserName.text = newName
                                Toast.makeText(context, "Name updated!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to update: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // --- Delete Account ---
        btnDeleteAccount.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Delete Account")
                .setMessage("Are you absolutely sure? This action is permanent and cannot be undone.")
                .setPositiveButton("Delete Forever") { _, _ ->
                    if (user != null) {
                        user.delete().addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                // Clear data
                                val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                                    com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                                ).build()
                                val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(requireActivity(), gso)
                                googleSignInClient.signOut()

                                prefs.edit().clear().apply()

                                Toast.makeText(context, "Account deleted successfully.", Toast.LENGTH_LONG).show()

                                val intent = Intent(requireContext(), LoginActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                requireActivity().finish()
                            } else {
                                Toast.makeText(context, "Delete failed. Please log out, log back in, and try again. Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // --- Privacy Policy ---
        view.findViewById<LinearLayout>(R.id.btnPrivacyPolicyLayout)?.setOnClickListener {
            showPrivacyPolicyDialog()
        }

        // --- Help & Support ---
        view.findViewById<LinearLayout>(R.id.btnHelpSupportLayout)?.setOnClickListener {
            showHelpSupportDialog()
        }

        // --- Logout ---
        btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes") { _, _ ->
                    FirebaseAuth.getInstance().signOut()
                    
                    // Sign out of Google completely to force account picker next time
                    val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                        com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                    ).build()
                    val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(requireActivity(), gso)
                    googleSignInClient.signOut()

                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    requireActivity().finish()
                }
                .setNegativeButton("No", null)
                .show()
        }
    }

    // ── Privacy Policy Dialog ─────────────────────────────────────────────────
    private fun showPrivacyPolicyDialog() {
        val policy = """
OffloadX Privacy Policy
Last updated: April 2026

1. DATA WE COLLECT
   • Authentication data: your email address and display name via Firebase Auth.
   • Files you upload for edge processing (images, videos, PDFs, text).
   • Device metrics: battery level and network type (used locally for routing decisions — never sent to external servers).
   • Task history: locally stored on your device via SQLite.

2. HOW WE USE YOUR DATA
   • Files are uploaded to your configured Edge Node (a local server on your own network) or to our simulated cloud endpoint solely for the purpose of processing.
   • Processed results are stored under the /media/processed/ folder on your Edge Node and are accessible only to you via the app.
   • We do NOT sell, rent, or share your data with any third parties.

3. DATA STORAGE AND RETENTION
   • Processed files remain on the Edge Node until you or the server administrator deletes them.
   • Firebase Auth tokens are managed by Google Firebase and are subject to Google's Privacy Policy.
   • Local task history can be cleared from the Downloads tab at any time.

4. SECURITY
   • All hub communication occurs over your local WiFi network.
   • We recommend running the Edge Node server only on trusted private networks.
   • No plaintext passwords are stored — authentication is handled by Firebase.

5. YOUR RIGHTS
   • You may delete your account at any time from the Profile screen, which removes your authentication record from Firebase.
   • You may clear all local history via the Downloads tab.
   • For any data removal requests, contact us at offloadxhelp@gmail.com.

6. CHILDREN'S PRIVACY
   OffloadX is not intended for children under 13. We do not knowingly collect data from minors.

7. CHANGES TO THIS POLICY
   We may update this policy from time to time. Significant changes will be notified in-app.

8. CONTACT
   Email: offloadxhelp@gmail.com
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("Privacy Policy")
            .setMessage(policy)
            .setPositiveButton("Got it", null)
            .show()
    }

    // ── Help & Support Dialog ─────────────────────────────────────────────────
    private fun showHelpSupportDialog() {
        val helpText = """
Need help? We're here for you!

Contact Us
   Email: offloadxhelp@gmail.com
   Response time: within 24–48 hours

─────────────────────────────
FREQUENTLY ASKED QUESTIONS

Q: The edge node shows "Not connected"?
A: Make sure your laptop server is running (run_server.bat) and your phone is on the same WiFi. Set the correct IP in Profile → Hub IP Address.

Q: Processing fails with a timeout?
A: Large video files may take time. Increase the read timeout or try a smaller file. Make sure the server console shows no errors.

Q: Can I use cloud mode without a laptop?
A: Yes! Select "Auto" routing - if WiFi is available and you check "Cloud Backup", it routes to the simulated cloud endpoint automatically.

Q: How do I clear my processed file history?
A: Go to the Downloads tab → triple-dot menu → Clear All.

Q: What file types are supported?
A: Images (JPG/PNG/WebP), Videos (MP4/MKV), PDFs, Text/CSV/JSON files, and Word/Excel documents.

Q: Why does the decision engine sometimes pick Local?
A: The engine picks Local when battery is critically low (<=5%), when the device is offline, or when on mobile data with files >50 MB to save bandwidth.

─────────────────────────────
Quick Tips
• Tap the Edge Node Status "Ping" button in the Dashboard to verify connection.
• Use "Manual" routing to force a specific node for testing.
• The Stats tab shows benchmark comparisons between Local, Hub, and Cloud.
        """.trimIndent()

        val scrollView = android.widget.ScrollView(requireContext())
        val tv = android.widget.TextView(requireContext()).apply {
            text = helpText
            textSize = 13f
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary))
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        scrollView.addView(tv)

        AlertDialog.Builder(requireContext())
            .setTitle(" Help & Support")
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .setNeutralButton("Send Email") { _, _ ->
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:offloadxhelp@gmail.com")
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "OffloadX Support Request")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "No email app found. Please email: offloadxhelp@gmail.com", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }
}