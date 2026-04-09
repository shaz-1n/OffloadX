package com.example.offload

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.offload.OffloadLogContract.LogEntry

/**
 * Thin repository around the offload execution‑log database.
 *
 * All public methods are **synchronous** — call them from a background thread
 * (Dispatchers.IO) or from a coroutine scope that already runs off the main
 * thread.  Keeping this layer synchronous avoids embedding lifecycle or
 * coroutine dependencies inside the data tier.
 */
class OffloadLogRepository(context: Context) {

    private val dbHelper = OffloadLogDbHelper.getInstance(context)

    // ─────────────────────────────────────────────────────────────────────
    // INSERT
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Log a completed (or failed) task execution.
     *
     * @param taskName       User‑given title of the task.
     * @param dataType       SIMPLE | COMPOSITE | COMPLEX
     * @param processingNode LOCAL | HUB | CLOUD
     * @param startTimeMs    System.currentTimeMillis() at task start.
     * @param endTimeMs      System.currentTimeMillis() at task end.
     * @param status         SUCCESS | FAILURE
     * @param inputSizeMB    Original file size in megabytes.
     * @return row ID of the newly inserted log, or ‑1 on error.
     */
    fun insertLog(
        taskName: String,
        dataType: String,
        processingNode: String,
        startTimeMs: Long,
        endTimeMs: Long,
        status: String = "SUCCESS",
        inputSizeMB: Double = 0.0
    ): Long {
        val elapsed = endTimeMs - startTimeMs
        val values = ContentValues().apply {
            put(LogEntry.COL_TASK_NAME, taskName)
            put(LogEntry.COL_DATA_TYPE, dataType.uppercase())
            put(LogEntry.COL_PROCESSING_NODE, processingNode.uppercase())
            put(LogEntry.COL_START_TIME, startTimeMs)
            put(LogEntry.COL_END_TIME, endTimeMs)
            put(LogEntry.COL_ELAPSED_MS, elapsed)
            put(LogEntry.COL_STATUS, status.uppercase())
            put(LogEntry.COL_INPUT_SIZE_MB, inputSizeMB)
        }
        return dbHelper.writableDatabase.insert(LogEntry.TABLE_NAME, null, values)
    }

    // ─────────────────────────────────────────────────────────────────────
    // QUERY — Fastest Processing Method
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns the processing node (LOCAL / HUB / CLOUD) that has the lowest
     * **average** elapsed time for the given [dataType], considering only
     * successful executions.
     *
     * @return a [FastestResult] with the winner, or `null` if no logs exist.
     */
    fun getFastestMethodForDataType(dataType: String): FastestResult? {
        val db = dbHelper.readableDatabase
        val query = """
            SELECT ${LogEntry.COL_PROCESSING_NODE},
                   AVG(${LogEntry.COL_ELAPSED_MS}) AS avg_ms,
                   COUNT(*)                         AS run_count
            FROM   ${LogEntry.TABLE_NAME}
            WHERE  ${LogEntry.COL_DATA_TYPE}  = ?
              AND  ${LogEntry.COL_STATUS}     = 'SUCCESS'
            GROUP  BY ${LogEntry.COL_PROCESSING_NODE}
            ORDER  BY avg_ms ASC
            LIMIT  1
        """.trimIndent()

        val cursor: Cursor = db.rawQuery(query, arrayOf(dataType.uppercase()))
        return cursor.use {
            if (it.moveToFirst()) {
                FastestResult(
                    processingNode = it.getString(0),
                    averageMs = it.getDouble(1),
                    runCount = it.getInt(2)
                )
            } else null
        }
    }

    data class FastestResult(
        val processingNode: String,
        val averageMs: Double,
        val runCount: Int
    )

    // ─────────────────────────────────────────────────────────────────────
    // QUERY — Average Elapsed per (DataType × ProcessingNode) for Reporting
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns a list of [ReportRow]s — one per unique (dataType, node) pair —
     * with the average elapsed time for successful runs.  This is exactly the
     * data set needed to draw a **grouped bar chart** in the Report Section.
     */
    fun getPerformanceReport(): List<ReportRow> {
        val db = dbHelper.readableDatabase
        val query = """
            SELECT ${LogEntry.COL_DATA_TYPE},
                   ${LogEntry.COL_PROCESSING_NODE},
                   AVG(${LogEntry.COL_ELAPSED_MS})  AS avg_ms,
                   MIN(${LogEntry.COL_ELAPSED_MS})  AS min_ms,
                   MAX(${LogEntry.COL_ELAPSED_MS})  AS max_ms,
                   COUNT(*)                          AS run_count
            FROM   ${LogEntry.TABLE_NAME}
            WHERE  ${LogEntry.COL_STATUS} = 'SUCCESS'
            GROUP  BY ${LogEntry.COL_DATA_TYPE}, ${LogEntry.COL_PROCESSING_NODE}
            ORDER  BY ${LogEntry.COL_DATA_TYPE}, avg_ms ASC
        """.trimIndent()

        val rows = mutableListOf<ReportRow>()
        db.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(
                    ReportRow(
                        dataType = cursor.getString(0),
                        processingNode = cursor.getString(1),
                        avgMs = cursor.getDouble(2),
                        minMs = cursor.getLong(3),
                        maxMs = cursor.getLong(4),
                        runCount = cursor.getInt(5)
                    )
                )
            }
        }
        return rows
    }

    data class ReportRow(
        val dataType: String,
        val processingNode: String,
        val avgMs: Double,
        val minMs: Long,
        val maxMs: Long,
        val runCount: Int
    )

    // ─────────────────────────────────────────────────────────────────────
    // QUERY — Recent logs (for scrolling list / debugging)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns the [limit] most recent log entries (newest first).
     */
    fun getRecentLogs(limit: Int = 50): List<LogRow> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            LogEntry.TABLE_NAME,
            null,       // all columns
            null, null, // no WHERE
            null, null, // no GROUP / HAVING
            "${LogEntry.COL_END_TIME} DESC",
            "$limit"
        )
        val rows = mutableListOf<LogRow>()
        cursor.use {
            while (it.moveToNext()) {
                rows.add(
                    LogRow(
                        id = it.getLong(it.getColumnIndexOrThrow(LogEntry.COL_ID)),
                        taskName = it.getString(it.getColumnIndexOrThrow(LogEntry.COL_TASK_NAME)),
                        dataType = it.getString(it.getColumnIndexOrThrow(LogEntry.COL_DATA_TYPE)),
                        processingNode = it.getString(it.getColumnIndexOrThrow(LogEntry.COL_PROCESSING_NODE)),
                        startTime = it.getLong(it.getColumnIndexOrThrow(LogEntry.COL_START_TIME)),
                        endTime = it.getLong(it.getColumnIndexOrThrow(LogEntry.COL_END_TIME)),
                        elapsedMs = it.getLong(it.getColumnIndexOrThrow(LogEntry.COL_ELAPSED_MS)),
                        status = it.getString(it.getColumnIndexOrThrow(LogEntry.COL_STATUS)),
                        inputSizeMB = it.getDouble(it.getColumnIndexOrThrow(LogEntry.COL_INPUT_SIZE_MB))
                    )
                )
            }
        }
        return rows
    }

    data class LogRow(
        val id: Long,
        val taskName: String,
        val dataType: String,
        val processingNode: String,
        val startTime: Long,
        val endTime: Long,
        val elapsedMs: Long,
        val status: String,
        val inputSizeMB: Double
    )

    // ─────────────────────────────────────────────────────────────────────
    // QUERY — Total Statistics Summary
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Quick aggregate stats across all log entries.
     */
    fun getOverallStats(): OverallStats {
        val db = dbHelper.readableDatabase
        val query = """
            SELECT COUNT(*),
                   COALESCE(SUM(${LogEntry.COL_ELAPSED_MS}), 0),
                   COALESCE(AVG(${LogEntry.COL_ELAPSED_MS}), 0),
                   COALESCE(SUM(${LogEntry.COL_INPUT_SIZE_MB}), 0),
                   SUM(CASE WHEN ${LogEntry.COL_STATUS} = 'SUCCESS' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN ${LogEntry.COL_STATUS} = 'FAILURE' THEN 1 ELSE 0 END)
            FROM ${LogEntry.TABLE_NAME}
        """.trimIndent()

        db.rawQuery(query, null).use { cursor ->
            return if (cursor.moveToFirst()) {
                OverallStats(
                    totalTasks = cursor.getInt(0),
                    totalElapsedMs = cursor.getLong(1),
                    avgElapsedMs = cursor.getDouble(2),
                    totalInputSizeMB = cursor.getDouble(3),
                    successCount = cursor.getInt(4),
                    failureCount = cursor.getInt(5)
                )
            } else {
                OverallStats()
            }
        }
    }

    data class OverallStats(
        val totalTasks: Int = 0,
        val totalElapsedMs: Long = 0L,
        val avgElapsedMs: Double = 0.0,
        val totalInputSizeMB: Double = 0.0,
        val successCount: Int = 0,
        val failureCount: Int = 0
    )
}
