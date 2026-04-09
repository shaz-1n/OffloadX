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

class FileAdapter(
    private var fileList: List<FileModel>,
    private val onDownloadSuccess: (String) -> Unit = {},
    private val onDeleteFile: (String) -> Unit = {},
    private val onSelectionChanged: (Int) -> Unit = {}
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    var isGridMode = false
    var isSelectionMode = false
        private set
    
    private val selectedPositions = mutableSetOf<Int>()
    private val expandedPositions = mutableSetOf<Int>()

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val layoutRes = if (isGridMode) R.layout.item_file_grid else R.layout.item_file
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val currentFile = fileList[position]

        holder.tvFileName.text = currentFile.fileName
        holder.tvFileSize.text = currentFile.fileSize
        holder.tvFileDate?.text = currentFile.fileDate

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

        // Download button state
        if (currentFile.isDownloaded) {
            holder.btnDownload.setImageResource(android.R.drawable.ic_menu_view)
            holder.btnDownload.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, android.R.color.holo_blue_dark)
            )
        } else {
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
                        "HUB"   -> Pair("🖥  Hub (Local Edge Node)",    "#CC2BC0A6")  // teal
                        "CLOUD" -> Pair("☁  Cloud (Simulated)",          "#CC7B52DD")  // purple
                        else    -> Pair("📱  Device (Local)",             "#CC888888")  // grey
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
            if (url.isNotEmpty() && url.startsWith("http")) {
                // For images specifically, open directly in browser for maximum compatibility
                val isImage = currentFile.fileType.lowercase() in listOf("image", "jpg", "png", "gif", "webp")
                if (isImage) {
                    try {
                        val browserIntent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url)
                        )
                        holder.itemView.context.startActivity(browserIntent)
                    } catch (e: Exception) {
                        Toast.makeText(holder.itemView.context, "No browser found to preview image.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    openFileInViewer(holder.itemView.context, url, currentFile.fileType)
                }
            } else if (currentFile.executionNode.uppercase() == "LOCAL") {
                Toast.makeText(holder.itemView.context,
                    "📱 Processed locally — results are computed on-device only. No file to preview.",
                    Toast.LENGTH_LONG).show()
            } else if (currentFile.isDownloaded) {
                try {
                    val intent = android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                    holder.itemView.context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(holder.itemView.context, "Open Downloads folder to view.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(holder.itemView.context,
                    "No preview available. Tap ⬇ Download to save the file first.",
                    Toast.LENGTH_SHORT).show()
            }
        }

        // Download button — downloads the file via DownloadManager, then opens it
        holder.btnDownload.setOnClickListener {
            // If already downloaded, open it
            if (currentFile.isDownloaded && currentFile.processedUrl.isNotEmpty()) {
                try {
                    val intent = android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                    holder.itemView.context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(holder.itemView.context, "Open Downloads folder to view.", Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }

            val url = currentFile.processedUrl
            if (url.isEmpty() || !url.startsWith("http")) {
                val msg = when (currentFile.executionNode.uppercase()) {
                    "LOCAL" -> "📱 Processed locally on device — no server file to download."
                    else    -> "Download link unavailable. The task may have failed on the server."
                }
                Toast.makeText(holder.itemView.context, msg, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            Toast.makeText(holder.itemView.context, "Downloading ${currentFile.fileName}...", Toast.LENGTH_SHORT).show()
            holder.btnDownload.setImageResource(android.R.drawable.ic_popup_sync)
            holder.btnDownload.isEnabled = false

            try {
                // Determine MIME for DownloadManager so Android knows how to open it
                val mime = getMimeType(currentFile.fileType)
                val uri = android.net.Uri.parse(url)
                val request = android.app.DownloadManager.Request(uri)
                request.setTitle(currentFile.fileName)
                request.setDescription("OffloadX Edge-Computed Result")
                request.setMimeType(mime)
                request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                    "${System.currentTimeMillis()}_${currentFile.fileName}"
                )
                val dm = holder.itemView.context.getSystemService(android.content.Context.DOWNLOAD_SERVICE)
                        as android.app.DownloadManager
                dm.enqueue(request)

                holder.itemView.postDelayed({
                    currentFile.isDownloaded = true
                    onDownloadSuccess(currentFile.processedUrl)
                    notifyItemChanged(position)
                }, 1500)
            } catch (e: Exception) {
                Toast.makeText(holder.itemView.context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
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
                Toast.makeText(context, "No app found to open this file type.", Toast.LENGTH_LONG).show()
            }
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
}