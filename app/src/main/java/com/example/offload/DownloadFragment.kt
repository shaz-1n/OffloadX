package com.example.offload

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DownloadFragment : Fragment(R.layout.fragment_download) {

    private val viewModel: SharedViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Find the RecyclerView by ID
        val rvFiles = view.findViewById<RecyclerView>(R.id.rvDownloadList)
        rvFiles.layoutManager = LinearLayoutManager(context)

        // 2. Observe the files
        viewModel.downloadableFiles.observe(viewLifecycleOwner) { files ->
            if (files.isEmpty()) {
                rvFiles.adapter = FileAdapter(listOf(
                    FileModel("No Files Processed Yet", "Waiting for Edge Node", "-", "doc")
                ))
            } else {
                val adapter = FileAdapter(
                    files.toList(), 
                    onDownloadSuccess = { url -> viewModel.markFileDownloaded(url) },
                    onDeleteFile = { url -> viewModel.removeFile(url) }
                )
                rvFiles.adapter = adapter
            }
        }
    }
}