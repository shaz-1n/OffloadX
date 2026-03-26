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
}