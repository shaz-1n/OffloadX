package com.example.offload.viewmodel
import com.example.offload.R

import com.example.offload.ui.auth.*
import com.example.offload.ui.main.*
import com.example.offload.ui.adapter.*
import com.example.offload.viewmodel.*
import com.example.offload.model.*
import com.example.offload.data.local.*
import com.example.offload.data.remote.*
import com.example.offload.engine.*



import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.AndroidViewModel
import android.app.Application
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SharedViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("OffloadX_History", Context.MODE_PRIVATE)

    private val _tasks = MutableLiveData<MutableList<Task>>(mutableListOf())
    val tasks: LiveData<MutableList<Task>> = _tasks
    
    private val _downloadableFiles = MutableLiveData<MutableList<FileModel>>(mutableListOf())
    val downloadableFiles: LiveData<MutableList<FileModel>> = _downloadableFiles

    init {
        loadHistoryFromDisk()
    }

    fun addTask(title: String, description: String) {
        val currentList = _tasks.value ?: mutableListOf()
        currentList.add(0, Task(title = title, description = description))
        _tasks.value = currentList
        saveHistoryToDisk()
    }

    val sharedUri = MutableLiveData<android.net.Uri?>()
    
    fun setSharedUri(uri: android.net.Uri?) {
        sharedUri.value = uri
    }

    fun addFile(file: FileModel) {
        val currentList = _downloadableFiles.value ?: mutableListOf()
        currentList.add(0, file)
        _downloadableFiles.value = currentList
        saveHistoryToDisk()
    }

    fun markFileDownloaded(processedUrl: String) {
        val currentList = _downloadableFiles.value ?: mutableListOf()
        for (file in currentList) {
            if (file.processedUrl == processedUrl) {
                file.isDownloaded = true
                break
            }
        }
        _downloadableFiles.value = currentList
        saveHistoryToDisk()
    }

    fun removeFile(processedUrl: String) {
        val currentList = _downloadableFiles.value ?: mutableListOf()
        currentList.removeAll { it.processedUrl == processedUrl }
        _downloadableFiles.value = currentList
        saveHistoryToDisk()
    }

    fun removeFiles(urls: Set<String>) {
        val currentList = _downloadableFiles.value ?: mutableListOf()
        currentList.removeAll { it.processedUrl in urls }
        _downloadableFiles.value = currentList
        saveHistoryToDisk()
    }

    fun clearAllFiles() {
        _downloadableFiles.value = mutableListOf()
        saveHistoryToDisk()
    }

    // --- PERSISTENCE ---
    private fun saveHistoryToDisk() {
        val tasksArray = JSONArray()
        _tasks.value?.forEach { task ->
            val obj = JSONObject()
            obj.put("title", task.title)
            obj.put("description", task.description)
            tasksArray.put(obj)
        }

        val filesArray = JSONArray()
        _downloadableFiles.value?.forEach { file ->
            val obj = JSONObject()
            obj.put("fileName", file.fileName)
            obj.put("fileSize", file.fileSize)
            obj.put("fileDate", file.fileDate)
            obj.put("fileType", file.fileType)
            obj.put("isDownloaded", file.isDownloaded)
            obj.put("processedUrl", file.processedUrl)
            obj.put("description", file.description)
            obj.put("processingTimeMs", file.processingTimeMs)
            obj.put("dataSizeMB", file.dataSizeMB)
            obj.put("taskType", file.taskType)
            obj.put("timestamp", file.timestamp)
            obj.put("executionNode", file.executionNode)
            filesArray.put(obj)
        }

        prefs.edit()
            .putString("tasks_json", tasksArray.toString())
            .putString("files_json", filesArray.toString())
            .apply()
    }

    private fun loadHistoryFromDisk() {
        val tasksStr = prefs.getString("tasks_json", "[]")
        val filesStr = prefs.getString("files_json", "[]")

        try {
            val loadedTasks = mutableListOf<Task>()
            val tasksArray = JSONArray(tasksStr)
            for (i in 0 until tasksArray.length()) {
                val obj = tasksArray.getJSONObject(i)
                loadedTasks.add(Task(title = obj.getString("title"), description = obj.getString("description")))
            }
            _tasks.value = loadedTasks

            val loadedFiles = mutableListOf<FileModel>()
            val filesArray = JSONArray(filesStr)
            for (i in 0 until filesArray.length()) {
                val obj = filesArray.getJSONObject(i)
                loadedFiles.add(
                    FileModel(
                        fileName = obj.getString("fileName"),
                        fileSize = obj.getString("fileSize"),
                        fileDate = obj.getString("fileDate"),
                        fileType = obj.getString("fileType"),
                        isDownloaded = obj.optBoolean("isDownloaded", false),
                        processedUrl = obj.optString("processedUrl", ""),
                        description = obj.optString("description", ""),
                        processingTimeMs = obj.optLong("processingTimeMs", 0L),
                        dataSizeMB = obj.optDouble("dataSizeMB", 0.0),
                        taskType = obj.optString("taskType", "COMPOSITE"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        executionNode = obj.optString("executionNode", "LOCAL")
                    )
                )
            }
            _downloadableFiles.value = loadedFiles
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}