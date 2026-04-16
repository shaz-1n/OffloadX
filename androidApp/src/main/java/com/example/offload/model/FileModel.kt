package com.example.offload.model
import com.example.offload.R

import com.example.offload.ui.auth.*
import com.example.offload.ui.main.*
import com.example.offload.ui.adapter.*
import com.example.offload.viewmodel.*
import com.example.offload.model.*
import com.example.offload.data.local.*
import com.example.offload.data.remote.*
import com.example.offload.engine.*



data class FileModel(
    val fileName: String,
    val fileSize: String,
    val fileDate: String,
    val fileType: String, // "pdf", "image", "jpg", "video", "mp4", "doc"
    var isDownloaded: Boolean = false,
    val processedUrl: String = "",
    val description: String = "",         // Task description / processing summary
    val processingTimeMs: Long = 0L,      // How long the edge node took to process
    val dataSizeMB: Double = 0.0,         // Original file size in MB
    val taskType: String = "COMPOSITE",   // COMPOSITE / COMPLEX / CUSTOM_CODE
    val timestamp: Long = System.currentTimeMillis(), // When the task was created
    val executionNode: String = "LOCAL"  // "LOCAL", "HUB", or "CLOUD"
)