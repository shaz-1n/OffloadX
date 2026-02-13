package com.example.offload

import android.util.Log

class DecisionEngine {

    companion object {
        private const val TAG = "DecisionEngine"

        // --- Device Constraints (Simulated Phone Specs) ---
        private const val CPU_SPEED_MOBILE = 2.0  // GHz
        private const val CPU_SPEED_CLOUD = 10.0  // GHz
        private const val WIFI_BANDWIDTH_MBPS = 5.0 // Average upload speed

        // --- User Preference Weights (0.0 to 1.0) ---
        private const val WEIGHT_TIME = 0.5
        private const val WEIGHT_ENERGY = 0.5

        // --- Energy Consumption Models (Watts) ---
        private const val POWER_CPU_ACTIVE = 1.5
        private const val POWER_WIFI_ACTIVE = 0.8
        private const val POWER_IDLE = 0.1
    }

    /**
     * Main Logic Function.
     * Returns: "CLOUD" or "LOCAL"
     */
    fun decide(taskName: String, dataSizeMB: Double, complexityCycles: Double): String {
        Log.d(TAG, "--- Analyzing Task: $taskName (Size: $dataSizeMB MB) ---")

        val (localCost, tLocal, eLocal) = estimateLocalCost(complexityCycles)
        val (cloudCost, tCloud, eCloud) = estimateCloudCost(dataSizeMB, complexityCycles)

        Log.d(TAG, "[LOCAL] Time: %.4fs | Energy: %.4fJ | Score: %.4f".format(tLocal, eLocal, localCost))
        Log.d(TAG, "[CLOUD] Time: %.4fs | Energy: %.4fJ | Score: %.4f".format(tCloud, eCloud, cloudCost))

        return if (cloudCost < localCost) {
            val benefit = ((localCost - cloudCost) / localCost) * 100
            Log.d(TAG, "👉 DECISION: OFFLOAD TO CLOUD (Benefit: %.1f%%)".format(benefit))
            "CLOUD"
        } else {
            Log.d(TAG, "👉 DECISION: EXECUTE LOCALLY (Network overhead is too high)")
            "LOCAL"
        }
    }

    private fun estimateLocalCost(cycles: Double): Triple<Double, Double, Double> {
        val time = cycles / (CPU_SPEED_MOBILE * 1e9) // seconds
        val energy = POWER_CPU_ACTIVE * time // Joules
        val cost = (WEIGHT_TIME * time) + (WEIGHT_ENERGY * energy)
        return Triple(cost, time, energy)
    }

    private fun estimateCloudCost(sizeMB: Double, cycles: Double): Triple<Double, Double, Double> {
        // 1. Transmission Time
        val txTime = sizeMB / WIFI_BANDWIDTH_MBPS
        
        // 2. Remote Processing Time
        val processTime = cycles / (CPU_SPEED_CLOUD * 1e9)
        
        // 3. Latency
        val rtt = 0.050 

        val totalTime = txTime + processTime + rtt
        val energy = (POWER_WIFI_ACTIVE * txTime) + (POWER_IDLE * (processTime + rtt))
        
        val cost = (WEIGHT_TIME * totalTime) + (WEIGHT_ENERGY * energy)
        return Triple(cost, totalTime, energy)
    }
}
