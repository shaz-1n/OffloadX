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



import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class ChangePasswordFragment : Fragment() {

    private var etNewPassword: TextInputEditText? = null
    private var btnUpdatePassword: Button? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_change_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etNewPassword = view.findViewById(R.id.etNewPassword)
        btnUpdatePassword = view.findViewById(R.id.btnUpdatePassword)

        btnUpdatePassword?.setOnClickListener {
            val newPass = etNewPassword?.text.toString()

            // Validation
            if (newPass.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a new password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPass.length < 6) {
                Toast.makeText(requireContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!newPass.any { it.isDigit() }) {
                Toast.makeText(requireContext(), "Password must contain at least one number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Firebase: Update password
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                btnUpdatePassword?.isEnabled = false
                btnUpdatePassword?.text = "Updating..."

                user.updatePassword(newPass)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(requireContext(), "Password updated successfully!", Toast.LENGTH_SHORT).show()
                            // Go back
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                        } else {
                            val error = task.exception?.message ?: "Failed to update password"
                            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                        }
                        btnUpdatePassword?.isEnabled = true
                        btnUpdatePassword?.text = "Update"
                    }
            } else {
                Toast.makeText(requireContext(), "Not logged in!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        etNewPassword = null
        btnUpdatePassword = null
    }
}