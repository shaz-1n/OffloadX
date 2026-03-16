package com.example.offload

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.AndroidViewModel
import android.app.Application
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SharedViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("OffloadX_History", Context.MODE_PRIVATE)

    // This list now automatically uses the Task.kt file we made
    private val _tasks = MutableLiveData<MutableList<Task>>(mutableListOf())
    val tasks: LiveData<MutableList<Task>> = _tasks
    
    // Dynamic Download Files List
    private val _downloadableFiles = MutableLiveData<MutableList<FileModel>>(mutableListOf())
    val downloadableFiles: LiveData<MutableList<FileModel>> = _downloadableFiles

    init {
        loadHistoryFromDisk()
    }

    fun addTask(title: String, description: String) {
        val currentList = _tasks.value ?: mutableListOf()
        currentList.add(0, Task(title = title, description = description)) // Add to top
        _tasks.value = currentList
        saveHistoryToDisk()
    }

    // Phase 1: Direct Redirection state to hold files shared into the app
    val sharedUri = MutableLiveData<android.net.Uri?>()
    
    fun setSharedUri(uri: android.net.Uri?) {
        sharedUri.value = uri
    }

    fun addFile(file: FileModel) {
        val currentList = _downloadableFiles.value ?: mutableListOf()
        // Add new files to the top of the list
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

    // --- PERSISTENCE LOGIC (Long Term Save on App Close) ---
    private fun saveHistoryToDisk() {
        // Save Tasks
        val tasksArray = JSONArray()
        _tasks.value?.forEach { task ->
            val obj = JSONObject()
            obj.put("title", task.title)
            obj.put("description", task.description)
            tasksArray.put(obj)
        }

        // Save Files
        val filesArray = JSONArray()
        _downloadableFiles.value?.forEach { file ->
            val obj = JSONObject()
            obj.put("fileName", file.fileName)
            obj.put("fileSize", file.fileSize)
            obj.put("fileDate", file.fileDate)
            obj.put("fileType", file.fileType)
            obj.put("isDownloaded", file.isDownloaded)
            obj.put("processedUrl", file.processedUrl)
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
                        processedUrl = obj.optString("processedUrl", "")
                    )
                )
            }
            _downloadableFiles.value = loadedFiles
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}