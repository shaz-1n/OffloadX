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
import androidx.lifecycle.ViewModel

class TaskViewModel : ViewModel() {
    private val _taskCount = MutableLiveData<Int>(0)
    val taskCount: LiveData<Int> = _taskCount

    fun addTask() {
        _taskCount.value = (_taskCount.value ?: 0) + 1
    }
}