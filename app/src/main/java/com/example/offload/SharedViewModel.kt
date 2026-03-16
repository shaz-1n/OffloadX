package com.example.offload

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {
    // This list now automatically uses the Task.kt file we made
    private val _tasks = MutableLiveData<MutableList<Task>>(mutableListOf())
    val tasks: LiveData<MutableList<Task>> = _tasks

    fun addTask(title: String, description: String) {
        val currentList = _tasks.value ?: mutableListOf()
        currentList.add(Task(title = title, description = description))
        _tasks.value = currentList
    }

    // Phase 1: Direct Redirection state to hold files shared into the app
    val sharedUri = MutableLiveData<android.net.Uri?>()
    
    fun setSharedUri(uri: android.net.Uri?) {
        sharedUri.value = uri
    }
}