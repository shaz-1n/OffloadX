package com.example.offload

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DownloadFragment : Fragment(R.layout.fragment_download) {

    private val viewModel: SharedViewModel by activityViewModels()
    
    private var isGridView = false
    private var sortByRecent = true
    private var isSearchVisible = false
    private var adapter: FileAdapter? = null
    private var allFiles: List<FileModel> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvFiles = view.findViewById<RecyclerView>(R.id.rvDownloadList)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val searchCard = view.findViewById<MaterialCardView>(R.id.searchCard)
        val btnSearch = view.findViewById<ImageButton>(R.id.btnSearch)
        val btnCloseSearch = view.findViewById<ImageButton>(R.id.btnCloseSearch)
        val btnSortHeader = view.findViewById<ImageButton>(R.id.btnSortHeader)
        val tvSortLabel = view.findViewById<TextView>(R.id.tvSortLabel)
        val btnToggleView = view.findViewById<ImageButton>(R.id.btnToggleView)
        val btnDeleteSelected = view.findViewById<ImageButton>(R.id.btnDeleteSelected)
        val tvFileCount = view.findViewById<TextView>(R.id.tvFileCount)
        val emptyState = view.findViewById<LinearLayout>(R.id.emptyState)

        // Setup initial layout
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

        // ── Search Icon: Toggle search bar visibility ──
        btnSearch.setOnClickListener {
            if (!isSearchVisible) {
                searchCard.visibility = View.VISIBLE
                searchCard.alpha = 0f
                searchCard.animate().alpha(1f).setDuration(200).start()
                etSearch.requestFocus()
                // Show keyboard
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                isSearchVisible = true
            } else {
                closeSearch(searchCard, etSearch)
            }
        }

        // Close search button (X inside search bar)
        btnCloseSearch.setOnClickListener {
            closeSearch(searchCard, etSearch)
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

        // ── Grid / List Toggle ──
        btnToggleView.setOnClickListener {
            isGridView = !isGridView
            adapter?.isGridMode = isGridView
            
            if (isGridView) {
                rvFiles.layoutManager = GridLayoutManager(context, 2)
                btnToggleView.setImageResource(R.drawable.ic_list_view)
            } else {
                rvFiles.layoutManager = LinearLayoutManager(context)
                btnToggleView.setImageResource(R.drawable.ic_grid_view)
            }
            adapter?.clearSelection()
            applyFilters(etSearch.text?.toString() ?: "")
        }

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

    private fun closeSearch(searchCard: MaterialCardView, etSearch: EditText) {
        searchCard.animate().alpha(0f).setDuration(150).withEndAction {
            searchCard.visibility = View.GONE
        }.start()
        etSearch.text.clear()
        // Hide keyboard
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
        isSearchVisible = false
        applyFilters("")
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