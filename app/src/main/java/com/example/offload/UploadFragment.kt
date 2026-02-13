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

    // Keep track of the mode: "COMPOSITE" or "COMPLEX"
    private var selectedMode: String = "COMPOSITE"

    // 1. Gallery Launcher (for Composite/Images)
    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            binding.tvFileStatus.text = "${uris.size} Image(s) Selected"
        }
    }

    // 2. File Explorer Launcher (for Complex/Documents)
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

        // Set COMPOSITE Mode
        binding.btnModeComposite.setOnClickListener {
            selectedMode = "COMPOSITE"
            binding.tvFileStatus.text = "Mode: Composite (Gallery)"
            Toast.makeText(context, "Gallery Mode Active", Toast.LENGTH_SHORT).show()
        }

        // Set COMPLEX Mode
        binding.btnModeComplex.setOnClickListener {
            selectedMode = "COMPLEX"
            binding.tvFileStatus.text = "Mode: Complex (File Explorer)"
            Toast.makeText(context, "File Explorer Mode Active", Toast.LENGTH_SHORT).show()
        }

        // THE MAGNIFYING GLASS LOGIC
        binding.btnSearchFile.setOnClickListener {
            if (selectedMode == "COMPOSITE") {
                // Open Gallery for Images only
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                // Open File Explorer for any document type
                filePickerLauncher.launch(arrayOf("*/*"))
            }
        }

        binding.btnFinalUpload.setOnClickListener {
            val title = binding.etTaskTitle.text.toString()
            if (title.isNotEmpty()) {
                viewModel.addTask(title, "Offload via $selectedMode")
                Toast.makeText(context, "Uploading...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}