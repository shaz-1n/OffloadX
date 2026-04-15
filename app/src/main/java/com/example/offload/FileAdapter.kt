package com.example.offload

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileAdapter(
    private var fileList: List<FileModel>,
    private val onDownloadSuccess: (String) -> Unit = {},
    private val onDeleteFile: (String) -> Unit = {},
    private val onSelectionChanged: (Int) -> Unit = {}
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    companion object {
        private const val VIEW_TYPE_LIST = 0
    }

    var isSelectionMode = false
        private set
    
    private val selectedPositions = mutableSetOf<Int>()
    private val expandedPositions = mutableSetOf<Int>()

    // Download tracking
    private val activeDownloadIds = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val activeDownloadProgress = java.util.concurrent.ConcurrentHashMap<Long, String>()
    private val failedDownloads = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val completedDownloadIds = java.util.concurrent.ConcurrentHashMap<String, Long>() // processedUrl -> dmId
    private var downloadJob: kotlinx.coroutines.Job? = null

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        val tvFileSize: TextView = itemView.findViewById(R.id.tvFileSize)
        val tvFileDate: TextView? = itemView.findViewById(R.id.tvFileDate)
        val ivFileIcon: ImageView? = itemView.findViewById(R.id.ivFileIcon)
        val btnDownload: ImageButton = itemView.findViewById(R.id.btnDownload)
        val cbSelect: CheckBox = itemView.findViewById(R.id.cbSelect)
        val btnView: ImageButton? = itemView.findViewById(R.id.btnView)
        
        // Detail panel (only in list layout)
        val detailPanel: LinearLayout? = itemView.findViewById(R.id.detailPanel)
        val tvDescription: TextView? = itemView.findViewById(R.id.tvDescription)
        val tvProcessingTime: TextView? = itemView.findViewById(R.id.tvProcessingTime)
        val tvTaskType: TextView? = itemView.findViewById(R.id.tvTaskType)
        val tvOriginalSize: TextView? = itemView.findViewById(R.id.tvOriginalSize)
        val tvExecutionNode: TextView? = itemView.findViewById(R.id.tvExecutionNode)
    }

    override fun getItemViewType(position: Int): Int = VIEW_TYPE_LIST

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val currentFile = fileList[position]

        holder.tvFileName.text = currentFile.fileName
        holder.tvFileSize.text = currentFile.fileSize
        holder.tvFileDate?.text = currentFile.fileDate

        // Show description subtitle in collapsed view (below file name row)
        holder.itemView.findViewById<TextView?>(R.id.tvDescriptionPreview)?.let { tv ->
            if (currentFile.description.isNotEmpty()) {
                tv.text = currentFile.description
                tv.visibility = View.VISIBLE
            } else {
                tv.visibility = View.GONE
            }
        }

        // Icon based on file type (list mode only)
        holder.ivFileIcon?.let { icon ->
            val iconRes = when (currentFile.fileType.lowercase()) {
                "pdf"               -> android.R.drawable.ic_menu_edit
                "image", "jpg",
                "png", "webp"       -> android.R.drawable.ic_menu_gallery
                "video", "mp4"      -> android.R.drawable.presence_video_online
                "text", "txt", "csv" -> android.R.drawable.ic_menu_agenda
                "doc", "docx",
                "xlsx"              -> android.R.drawable.ic_menu_manage
                else                -> android.R.drawable.ic_menu_save
            }
            icon.setImageResource(iconRes)
        }

        // Validate physical file presence before finalizing button UI
        // If they downloaded it then went into File Explorer and deleted it, the app should unfreeze the download state
        if (currentFile.isDownloaded && currentFile.processedUrl.isNotEmpty()) {
            val fileInDownloads = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                currentFile.fileName
            )
            if (!fileInDownloads.exists()) {
                currentFile.isDownloaded = false
            }
        }

        // Download button state
        val downloadIdObj = activeDownloadIds[currentFile.processedUrl]
        val activeProgress = if (downloadIdObj != null) activeDownloadProgress[downloadIdObj] else failedDownloads[currentFile.processedUrl]

        if (activeProgress != null) {
            holder.tvFileSize.text = activeProgress
            holder.btnDownload.setImageResource(android.R.drawable.ic_popup_sync)
            holder.btnDownload.isEnabled = false
            holder.btnDownload.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.primary_color)
            )
        } else if (currentFile.isDownloaded) {
            // Keep the download icon but gray it out to show it's already saved locally
            holder.btnDownload.isEnabled = true
            holder.btnDownload.setImageResource(R.drawable.ic_nav_download)
            holder.btnDownload.imageTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.GRAY
            )
        } else {
            holder.btnDownload.isEnabled = true
            holder.btnDownload.setImageResource(R.drawable.ic_nav_download)
            holder.btnDownload.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.primary_color)
            )
        }

        // Selection mode
        holder.cbSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        holder.cbSelect.isChecked = selectedPositions.contains(position)
        holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedPositions.add(position) else selectedPositions.remove(position)
            onSelectionChanged(selectedPositions.size)
        }

        // Expand/collapse detail panel (list mode only)
        holder.detailPanel?.let { panel ->
            val isExpanded = expandedPositions.contains(position)
            panel.visibility = if (isExpanded) View.VISIBLE else View.GONE
            
            if (isExpanded) {
                holder.tvDescription?.text = currentFile.description.ifEmpty { "No description available" }
                holder.tvProcessingTime?.text = if (currentFile.processingTimeMs > 0) "${currentFile.processingTimeMs}ms" else "—"
                holder.tvTaskType?.text = currentFile.taskType.ifEmpty { "—" }
                holder.tvOriginalSize?.text = if (currentFile.dataSizeMB > 0) "${"%.2f".format(currentFile.dataSizeMB)} MB" else "—"

                // Show where processing ran — pill badge tinted per tier
                holder.tvExecutionNode?.let { tv ->
                    val (label, tintHex) = when (currentFile.executionNode.uppercase()) {
                        "HUB"   -> Pair("Hub (Local Edge Node)",    "#CC2BC0A6")  // teal
                        "CLOUD" -> Pair("Cloud (Simulated)",          "#CC7B52DD")  // purple
                        else    -> Pair("Device (Local)",             "#CC888888")  // grey
                    }
                    tv.text = label
                    tv.setTextColor(android.graphics.Color.WHITE)
                    tv.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor(tintHex)
                    )
                    tv.visibility = View.VISIBLE
                }
            }
        }

        // Click to expand (list mode) or toggle selection
        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                holder.cbSelect.isChecked = !holder.cbSelect.isChecked
            } else {
                if (expandedPositions.contains(position)) {
                    expandedPositions.remove(position)
                } else {
                    expandedPositions.add(position)
                }
                notifyItemChanged(position)
            }
        }

        // Long press to enter selection mode
        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                isSelectionMode = true
                selectedPositions.clear()
                selectedPositions.add(position)
                onSelectionChanged(1)
                notifyDataSetChanged()
            }
            true
        }

        // View button — opens the file using the correct app based on MIME type
        holder.btnView?.setOnClickListener {
            val url = currentFile.processedUrl
            if (currentFile.isDownloaded) {
                // File was downloaded — open via DownloadManager URI (same as notification bar)
                openDownloadedFile(holder.itemView.context, currentFile)
            } else if (url.isNotEmpty() && url.startsWith("http")) {
                // Not downloaded yet — for images, open remotely in browser
                val isImage = currentFile.fileType.lowercase() in listOf("image", "jpg", "png", "gif", "webp")
                if (isImage) {
                    try {
                        val browserIntent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url)
                        )
                        holder.itemView.context.startActivity(browserIntent)
                    } catch (e: Exception) {
                        Toast.makeText(holder.itemView.context, "No browser found.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(holder.itemView.context, "Download the file first.", Toast.LENGTH_SHORT).show()
                }
            } else if (currentFile.executionNode.uppercase() == "LOCAL") {
                Toast.makeText(holder.itemView.context,
                    "Processed locally — no file.",
                    Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(holder.itemView.context,
                    "Download the file first.",
                    Toast.LENGTH_SHORT).show()
            }
        }

        // Download button — downloads the file via DownloadManager, then opens it
        holder.btnDownload.setOnClickListener {
            // Check immediately on click: did they delete it while the app was sitting here?
            if (currentFile.isDownloaded && currentFile.processedUrl.isNotEmpty()) {
                // Already downloaded — open it directly via DownloadManager URI
                openDownloadedFile(holder.itemView.context, currentFile)
                return@setOnClickListener
            }

            val url = currentFile.processedUrl
            if (url.isEmpty() || !url.startsWith("http")) {
                val msg = when (currentFile.executionNode.uppercase()) {
                    "LOCAL" -> "Processed locally — no file to download."
                    else    -> "Download link unavailable."
                }
                Toast.makeText(holder.itemView.context, msg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(holder.itemView.context, "Downloading...", Toast.LENGTH_SHORT).show()
            holder.btnDownload.setImageResource(android.R.drawable.ic_popup_sync)
            holder.btnDownload.isEnabled = false

            try {
                val uri = android.net.Uri.parse(url)
                
                // Generate clean, readable filename based exactly on original upload, eg: "video.mp4"
                val finalFileName = currentFile.fileName

                // Force-wipe any ghost file in the Downloads directory matching this name to bypass DownloadManager conflict blocks
                val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val existingFile = java.io.File(downloadDir, finalFileName)
                var canEnqueue = true
                if (existingFile.exists()) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        try {
                            val resolver = holder.itemView.context.contentResolver
                            val queryUri = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                            val selection = "${android.provider.MediaStore.Downloads.DISPLAY_NAME} = ? AND ${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} = ?"
                            val targetPath = android.os.Environment.DIRECTORY_DOWNLOADS + "/"
                            val deletedRows = resolver.delete(queryUri, selection, arrayOf(finalFileName, targetPath))
                            if (deletedRows == 0 && existingFile.exists()) {
                                canEnqueue = false
                                android.util.Log.e("FileAdapter", "Cannot remove existing file, SAF required to overwrite $finalFileName")
                            }
                        } catch (e: Exception) {
                            canEnqueue = false
                            android.util.Log.e("FileAdapter", "MediaStore deletion failed for $finalFileName", e)
                        }
                    } else {
                        canEnqueue = existingFile.delete()
                    }
                }
                
                if (!canEnqueue) {
                    Toast.makeText(holder.itemView.context, "Clear existing file or grant permissions.", Toast.LENGTH_SHORT).show()
                    notifyItemChanged(position)
                    return@setOnClickListener
                }

                // Determine MIME for DownloadManager so Android knows how to open it
                val urlExt = url.substringAfterLast('.', "")
                val mime = if (urlExt.isNotEmpty() && urlExt.length <= 4) {
                    getMimeType(urlExt)
                } else {
                    getMimeType(currentFile.fileType)
                }
                
                val request = android.app.DownloadManager.Request(uri)
                request.setTitle(currentFile.fileName)
                request.setDescription("OffloadX Edge-Computed Result")
                request.setMimeType(mime)
                request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                request.setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                    finalFileName
                )
                val dm = holder.itemView.context.getSystemService(android.content.Context.DOWNLOAD_SERVICE)
                        as android.app.DownloadManager
                val downloadId = dm.enqueue(request)

                // Track download without modifying ViewHolder
                failedDownloads.remove(currentFile.processedUrl)
                activeDownloadIds[currentFile.processedUrl] = downloadId
                activeDownloadProgress[downloadId] = "Starting..."
                notifyItemChanged(position)

                if (downloadJob == null || downloadJob?.isActive != true) {
                    downloadJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        while (downloadJob?.isActive == true && activeDownloadIds.isNotEmpty()) {
                            val appContext = holder.itemView.context.applicationContext
                            val mgr = appContext.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                            var stateChanged = false
                            
                            val iter = activeDownloadIds.entries.iterator()
                            while (iter.hasNext()) {
                                val entry = iter.next()
                                val urlStr = entry.key
                                val dId = entry.value
                                
                                val q = android.app.DownloadManager.Query().setFilterById(dId)
                                val cursor = mgr.query(q)
                                if (cursor != null) {
                                    cursor.use {
                                        if (it.moveToFirst()) {
                                            val statusIndex = it.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS)
                                            val downloadedIndex = it.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                                            val totalIndex = it.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                                            
                                            if (statusIndex >= 0 && downloadedIndex >= 0 && totalIndex >= 0) {
                                                val status = it.getInt(statusIndex)
                                                val bytesDownloaded = it.getLong(downloadedIndex)
                                                val bytesTotal = it.getLong(totalIndex)
                                                
                                                if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                                                    iter.remove()
                                                    activeDownloadProgress.remove(dId)
                                                    stateChanged = true
                                                    completedDownloadIds[urlStr] = dId
                                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                        val fileObj = fileList.find { f -> f.processedUrl == urlStr }
                                                        if (fileObj != null) fileObj.isDownloaded = true
                                                        onDownloadSuccess(urlStr)
                                                    }
                                                } else if (status == android.app.DownloadManager.STATUS_FAILED) {
                                                    iter.remove()
                                                    activeDownloadProgress.remove(dId)
                                                    failedDownloads[urlStr] = "Download Failed"
                                                    stateChanged = true
                                                } else {
                                                    if (bytesTotal > 0L) {
                                                        val progress = ((bytesDownloaded * 100L) / bytesTotal).toInt()
                                                        val megaBytes = "%.2f".format(bytesDownloaded.toDouble() / (1024 * 1024))
                                                        activeDownloadProgress[dId] = "$progress% ($megaBytes MB)"
                                                    } else {
                                                        activeDownloadProgress[dId] = "Downloading..."
                                                    }
                                                    stateChanged = true
                                                }
                                            }
                                        } else {
                                            iter.remove()
                                            activeDownloadProgress.remove(dId)
                                            stateChanged = true
                                        }
                                    }
                                } else {
                                    iter.remove()
                                    activeDownloadProgress.remove(dId)
                                    stateChanged = true
                                }
                            }
                            
                            if (stateChanged) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    notifyDataSetChanged()
                                }
                            }
                            kotlinx.coroutines.delay(300)
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(holder.itemView.context, "Download failed.", Toast.LENGTH_SHORT).show()
                holder.btnDownload.isEnabled = true
                holder.btnDownload.setImageResource(R.drawable.ic_nav_download)
            }
        }
    }

    /** Opens a remote URL in the correct viewer app based on file type. */
    private fun openFileInViewer(context: android.content.Context, url: String, fileType: String) {
        try {
            val mime = getMimeType(fileType)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(android.net.Uri.parse(url), mime)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: open in browser
            try {
                val browserIntent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url)
                )
                context.startActivity(browserIntent)
            } catch (e2: Exception) {
                Toast.makeText(context, "No app to open this file.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Opens a downloaded file using DownloadManager's content URI (same way notification bar does it). */
    private fun openDownloadedFile(context: android.content.Context, file: FileModel) {
        val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val mime = getMimeType(file.fileType)

        // Try DownloadManager URI first (most reliable — same as notification bar)
        val dmId = completedDownloadIds[file.processedUrl]
        if (dmId != null) {
            val dmUri = dm.getUriForDownloadedFile(dmId)
            if (dmUri != null) {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(dmUri, mime)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                } catch (_: Exception) { /* fall through */ }
            }
        }

        // Fallback: find the file on disk and use FileProvider
        val downloadedFile = java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            file.fileName
        )
        if (downloadedFile.exists()) {
            try {
                val fileUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    context.packageName + ".provider",
                    downloadedFile
                )
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, mime)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "No app to open this file.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "File not found. Re-download it.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Maps our internal fileType string to a proper MIME type for intents. */
    private fun getMimeType(fileType: String): String = when (fileType.lowercase()) {
        "pdf"                   -> "application/pdf"
        "image", "jpg", "png",
        "gif", "webp", "bmp"    -> "image/*"
        "video", "mp4"          -> "video/*"
        "text", "txt"           -> "text/plain"
        "csv"                   -> "text/csv"
        "json"                  -> "application/json"
        "xml"                   -> "text/xml"
        "doc", "docx"           -> "application/msword"
        "xlsx"                  -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        else                    -> "application/octet-stream"
    }

    override fun getItemCount(): Int = fileList.size

    fun getSelectedUrls(): Set<String> {
        return selectedPositions.mapNotNull { pos ->
            fileList.getOrNull(pos)?.processedUrl
        }.toSet()
    }

    fun clearSelection() {
        isSelectionMode = false
        selectedPositions.clear()
        expandedPositions.clear()
        onSelectionChanged(0)
        notifyDataSetChanged()
    }

    fun updateList(newList: List<FileModel>) {
        fileList = newList
        selectedPositions.clear()
        expandedPositions.clear()
        notifyDataSetChanged()
    }
    fun cancelDownloads() {
        downloadJob?.cancel()
        activeDownloadIds.clear()
        activeDownloadProgress.clear()
        failedDownloads.clear()
    }
}