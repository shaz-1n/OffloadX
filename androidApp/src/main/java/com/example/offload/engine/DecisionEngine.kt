package com.example.offload.engine
import com.example.offload.R

import com.example.offload.ui.auth.*
import com.example.offload.ui.main.*
import com.example.offload.ui.adapter.*
import com.example.offload.viewmodel.*
import com.example.offload.model.*
import com.example.offload.data.local.*
import com.example.offload.data.remote.*
import com.example.offload.engine.*



import android.util.Log

enum class NetworkType { WIFI, MOBILE, OFFLINE }
enum class ExecutionRoute { LOCAL, HUB, CLOUD }

class DecisionEngine {

    companion object {
        private const val TAG = "DecisionEngine"
    }

    fun decide(
        taskName: String,
        dataSizeMB: Double,
        batteryPercent: Int,
        networkType: NetworkType,
        isCloudBackupNeeded: Boolean = false
    ): ExecutionRoute {
        
        Log.d(TAG, "Analyzing Task: $taskName | Size: ${"%.2f".format(dataSizeMB)}MB | Batt: $batteryPercent% | Net: $networkType")

        // 1. Battery Threshold Warning
        if (batteryPercent <= 5) {
            Log.w(TAG, "Battery critically low (<=5%). Forcing LOCAL to prevent radio power drain from large encryption/transmission.")
            return ExecutionRoute.LOCAL
        }

        // 2. Network offline check
        if (networkType == NetworkType.OFFLINE) {
             Log.d(TAG, "Device Offline. Queueing in LOCAL.")
             return ExecutionRoute.LOCAL
        }

        // 3. Heavy Data Routing (Or All Data for Testing/Demo)
        if (networkType == NetworkType.WIFI) {
             if (isCloudBackupNeeded) {
                  Log.d(TAG, "WiFi Active + Backup Required. Routing to CLOUD (Firebase).")
                  return ExecutionRoute.CLOUD
             } else {
                  Log.d(TAG, "WiFi Active. Routing to edge HUB (Laptop).")
                  return ExecutionRoute.HUB
             }
        } else if (networkType == NetworkType.MOBILE) {
             if (dataSizeMB > 50.0) {
                 Log.d(TAG, "Mobile Data Active. Restricting Heavy upload (>50MB). Routing to LOCAL Queue.")
                 return ExecutionRoute.LOCAL
             } else {
                 Log.d(TAG, "Mobile Data Active but file is within limit (<50MB). Routing to HUB.")
                 return ExecutionRoute.HUB
             }
        }
        
        return ExecutionRoute.LOCAL
    }
}
