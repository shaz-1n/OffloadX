package com.example.offload

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder


class DownloadFragment : Fragment(R.layout.fragment_download) {

    private val viewModel: SharedViewModel by activityViewModels()
    
    private var sortByRecent = true
    private var adapter: FileAdapter? = null
    private var allFiles: List<FileModel> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvFiles = view.findViewById<RecyclerView>(R.id.rvDownloadList)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val btnCloseSearch = view.findViewById<ImageButton>(R.id.btnCloseSearch)
        val btnSortHeader = view.findViewById<ImageButton>(R.id.btnSortHeader)
        val tvSortLabel = view.findViewById<TextView>(R.id.tvSortLabel)
        val btnDeleteSelected = view.findViewById<ImageButton>(R.id.btnDeleteSelected)
        val tvFileCount = view.findViewById<TextView>(R.id.tvFileCount)
        val emptyState = view.findViewById<LinearLayout>(R.id.emptyState)

        // Setup list layout (always list mode, no grid toggle)
        rvFiles.layoutManager = LinearLayoutManager(context)

        // Create adapter
        adapter = FileAdapter(
            emptyList(),
            onDownloadSuccess = { url -> viewModel.markFileDownloaded(url) },
            onDeleteFile = { url -> viewModel.removeFile(url) },
            onSelectionChanged = { count ->
                btnDeleteSelected.visibility = if (count > 0) View.VISIBLE else View.GONE
            }
        )
        rvFiles.adapter = adapter

        // Observe files
        viewModel.downloadableFiles.observe(viewLifecycleOwner) { files ->
            allFiles = files.toList()
            val query = etSearch.text?.toString() ?: ""
            applyFilters(query)
            
            tvFileCount.text = "${files.size} file${if (files.size != 1) "s" else ""}"
            emptyState.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
            rvFiles.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE
        }

        // Close/clear search button (X inside search bar)
        btnCloseSearch.setOnClickListener {
            etSearch.text.clear()
        }

        // Search text filter
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // ── Sort toggle (header icon OR label) ──
        val sortAction = View.OnClickListener {
            sortByRecent = !sortByRecent
            tvSortLabel.text = if (sortByRecent) "Recent" else "Oldest"
            applyFilters(etSearch.text?.toString() ?: "")
        }
        btnSortHeader.setOnClickListener(sortAction)
        tvSortLabel.setOnClickListener(sortAction)

        // ── Delete Selected ──
        btnDeleteSelected.setOnClickListener {
            val selectedUrls = adapter?.getSelectedUrls() ?: emptySet()
            if (selectedUrls.isEmpty()) return@setOnClickListener

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete ${selectedUrls.size} file(s)?")
                .setMessage("This will remove them from your history. Downloaded files on your device will remain.")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.removeFiles(selectedUrls)
                    adapter?.clearSelection()
                    Toast.makeText(context, "${selectedUrls.size} file(s) removed", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter?.cancelDownloads()
    }

    private fun applyFilters(query: String) {
        var filtered = allFiles.toList()
        
        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.fileName.contains(query, ignoreCase = true) ||
                it.fileType.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
            }
        }

        filtered = if (sortByRecent) {
            filtered.sortedByDescending { it.timestamp }
        } else {
            filtered.sortedBy { it.timestamp }
        }

        adapter?.updateList(filtered)
    }
}