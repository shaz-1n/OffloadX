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
}