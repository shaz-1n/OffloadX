package com.example.offload

data class FileModel(
    val fileName: String,
    val fileSize: String,
    val fileDate: String, // New field
    val fileType: String, // New field (e.g., "pdf", "image", "doc")
    var isDownloaded: Boolean = false, // Tracks physical file sync status
    val processedUrl: String = "" // To pipe the results straight to Media Player
)