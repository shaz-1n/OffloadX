package com.example.offload

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class FileAdapter(
    private val fileList: List<FileModel>,
    private val onDownloadSuccess: (String) -> Unit = {},
    private val onDeleteFile: (String) -> Unit = {}
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    // This class finds the views inside your item_file.xml
    // Change your ViewHolder class to look like this:
    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Specify the type <TextView> or <ImageView> so findViewById works
        val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        val tvFileSize: TextView = itemView.findViewById(R.id.tvFileSize)
        val tvFileDate: TextView = itemView.findViewById(R.id.tvFileDate)
        val ivFileIcon: ImageView = itemView.findViewById(R.id.ivFileIcon)
        val btnDownload: ImageButton = itemView.findViewById(R.id.btnDownload)
    }

    // This "inflates" the layout (turns the XML into a real UI object)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    // This puts the actual data (name/size) into the text fields
    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val currentFile = fileList[position]

        holder.tvFileName.text = currentFile.fileName
        holder.tvFileSize.text = currentFile.fileSize
        holder.tvFileDate.text = currentFile.fileDate // Now this works!

        // Logic for setting the icon based on file type
        val iconRes = when (currentFile.fileType.lowercase()) {
            "pdf" -> android.R.drawable.ic_menu_edit // Standard Android PDF-like icon
            "image", "jpg", "png" -> android.R.drawable.ic_menu_gallery
            "video", "mp4" -> android.R.drawable.presence_video_online
            else -> android.R.drawable.ic_menu_save
        }

        holder.ivFileIcon.setImageResource(iconRes)

        // State check if it's already downloaded locally
        if (currentFile.isDownloaded) {
            holder.btnDownload.setImageResource(android.R.drawable.ic_menu_view)
            holder.btnDownload.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, android.R.color.holo_blue_dark)
            )
            holder.btnDownload.isEnabled = true
        } else {
            holder.btnDownload.setImageResource(android.R.drawable.stat_sys_download)
            holder.btnDownload.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.primary_color)
            )
            holder.btnDownload.isEnabled = true
        }

        holder.btnDownload.setOnClickListener {
            // Already downloaded? Open the Native System Downloads Folder to view the JPG!
            if (currentFile.isDownloaded && currentFile.processedUrl.isNotEmpty()) {
                try {
                    val intent = android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                    holder.itemView.context.startActivity(intent)
                    Toast.makeText(holder.itemView.context, "Opening Downloads Directory. Tap on ${currentFile.fileName} to view Edge Insight!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(holder.itemView.context, "No app found to handle Downloads Folder.", Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }
        
            Toast.makeText(holder.itemView.context, "Downloading ${currentFile.fileName} from Hub Edge Storage...", Toast.LENGTH_SHORT).show()
            
            holder.btnDownload.setImageResource(android.R.drawable.ic_popup_sync)
            holder.btnDownload.isEnabled = false
            
            // --- ACTUAL PHYSICAL DOWNLOAD INSTEAD OF HTTP STREAMING ---
            if (currentFile.processedUrl.isEmpty() || !currentFile.processedUrl.startsWith("http")) {
                Toast.makeText(holder.itemView.context, "No valid URL found. This task failed to process.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            try {
                val uri = android.net.Uri.parse(currentFile.processedUrl)
                val request = android.app.DownloadManager.Request(uri)
                request.setTitle(currentFile.fileName)
                request.setDescription("Securely downloading Edge-Computed Result...")
                request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS, 
                    "${System.currentTimeMillis()}_${currentFile.fileName}"
                )

                val downloadManager = holder.itemView.context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                downloadManager.enqueue(request)
                
                // Immediately switch state to downloaded since the system is now handling it
                holder.itemView.postDelayed({
                    currentFile.isDownloaded = true
                    onDownloadSuccess(currentFile.processedUrl)
                    notifyItemChanged(position)
                    
                    com.google.android.material.snackbar.Snackbar.make(
                        holder.itemView, 
                        "Successfully initiated physical download! Check notification bar.", 
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).show()
                }, 1000)
                
            } catch (e: Exception) {
                Toast.makeText(holder.itemView.context, "Failed to start download process: ${e.message}", Toast.LENGTH_LONG).show()
                holder.btnDownload.isEnabled = true
                holder.btnDownload.setImageResource(android.R.drawable.stat_sys_download)
            }
        }

        // --- NEW DELETE CAPABILITY ---
        holder.itemView.setOnLongClickListener {
            onDeleteFile(currentFile.processedUrl)
            Toast.makeText(holder.itemView.context, "${currentFile.fileName} removed from History", Toast.LENGTH_SHORT).show()
            true
        }
    }

    // Tells the list how many items to show
    override fun getItemCount(): Int {
        return fileList.size
    }
}