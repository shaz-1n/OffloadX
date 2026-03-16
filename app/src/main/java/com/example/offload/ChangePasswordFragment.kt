package com.example.offload

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.offload.databinding.FragmentChangePasswordBinding
import com.google.firebase.auth.FirebaseAuth

class ChangePasswordFragment : Fragment() {

    private var _binding: FragmentChangePasswordBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChangePasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnUpdatePassword.setOnClickListener {
            val newPass = binding.etNewPassword.text.toString()

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
                binding.btnUpdatePassword.isEnabled = false
                binding.btnUpdatePassword.text = "Updating..."

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
                        binding.btnUpdatePassword.isEnabled = true
                        binding.btnUpdatePassword.text = "Update"
                    }
            } else {
                Toast.makeText(requireContext(), "Not logged in!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}