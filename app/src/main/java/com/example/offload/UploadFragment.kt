package com.example.offload

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.offload.databinding.FragmentUploadBinding

class UploadFragment : Fragment() {

    private var _binding: FragmentUploadBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SharedViewModel by activityViewModels()

    private var selectedMode: String = "COMPOSITE"

    // 1. Gallery Launcher (Composite)
    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            binding.tvFileStatus.text = "${uris.size} Image(s) Selected"
        }
    }

    // 2. File Explorer Launcher (Complex)
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            binding.tvFileStatus.text = "File: ${it.lastPathSegment}"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUploadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Automatically generate a Task ID when the screen opens
        binding.etTaskId.setText("TASK_${System.currentTimeMillis() / 1000}")

// If you want the Process ID to be random too:
        binding.etProcessId.setText("PROC_${java.util.UUID.randomUUID().toString().take(8)}")

        // --- MUTUAL EXCLUSION CHECKBOX LOGIC ---
        binding.cbLocal.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.cbCloud.isChecked = false // Deselect Cloud
            }
        }

        binding.cbCloud.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.cbLocal.isChecked = false // Deselect Local
            }
        }

        // --- MODE SELECTION ---
        binding.btnModeComposite.setOnClickListener {
            selectedMode = "COMPOSITE"
            binding.tvFileStatus.text = "Mode: Composite (Gallery)"
        }

        binding.btnModeComplex.setOnClickListener {
            selectedMode = "COMPLEX"
            binding.tvFileStatus.text = "Mode: Complex (File Explorer)"
        }

        // --- MAGNIFYING GLASS LOGIC ---
        binding.btnSearchFile.setOnClickListener {
            if (selectedMode == "COMPOSITE") {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                filePickerLauncher.launch(arrayOf("*/*"))
            }
        }

        // --- UPLOAD BUTTON ---
        binding.btnFinalUpload.setOnClickListener {
            val title = binding.etTaskTitle.text.toString()
            val location = if (binding.cbLocal.isChecked) "Local" else "Cloud"

            if (title.isNotEmpty()) {
                viewModel.addTask(title, "Mode: $selectedMode | Storage: $location")
                Toast.makeText(context, "Task Sent to $location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}