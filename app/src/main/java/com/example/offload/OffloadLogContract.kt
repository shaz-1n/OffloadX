package com.example.offload

import android.provider.BaseColumns

/**
 * Schema contract for the offload execution‐log database.
 *
 * Single lightweight table that stores every offloaded (or locally executed)
 * task together with its timing, status, data‐type classification, and input
 * size — all the fields needed to answer "What is the fastest processing
 * method for data‐type X?" and to render a Report Section chart.
 */
object OffloadLogContract {

    const val DATABASE_NAME = "offload_log.db"
    const val DATABASE_VERSION = 1

    object LogEntry : BaseColumns {
        const val TABLE_NAME = "execution_log"

        // ── Identifiers ──────────────────────────────────────────────────
        const val COL_ID            = BaseColumns._ID          // auto‑inc PK
        const val COL_TASK_NAME     = "task_name"              // user‑provided title

        // ── Classification ───────────────────────────────────────────────
        /** SIMPLE | COMPOSITE | COMPLEX */
        const val COL_DATA_TYPE     = "data_type"

        /** LOCAL | HUB | CLOUD */
        const val COL_PROCESSING_NODE = "processing_node"

        // ── Timing ───────────────────────────────────────────────────────
        /** epoch‑ms when the task started */
        const val COL_START_TIME    = "start_time"
        /** epoch‑ms when the task finished */
        const val COL_END_TIME      = "end_time"
        /** pre‑computed convenience: end − start (ms) */
        const val COL_ELAPSED_MS    = "elapsed_ms"

        // ── Result ───────────────────────────────────────────────────────
        /** SUCCESS | FAILURE */
        const val COL_STATUS        = "status"

        // ── File metadata ────────────────────────────────────────────────
        /** original input size in MB (REAL) */
        const val COL_INPUT_SIZE_MB = "input_size_mb"

        // ── SQL ──────────────────────────────────────────────────────────
        const val SQL_CREATE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID             INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TASK_NAME      TEXT    NOT NULL,
                $COL_DATA_TYPE      TEXT    NOT NULL DEFAULT 'COMPOSITE',
                $COL_PROCESSING_NODE TEXT   NOT NULL DEFAULT 'LOCAL',
                $COL_START_TIME     INTEGER NOT NULL,
                $COL_END_TIME       INTEGER NOT NULL,
                $COL_ELAPSED_MS     INTEGER NOT NULL,
                $COL_STATUS         TEXT    NOT NULL DEFAULT 'SUCCESS',
                $COL_INPUT_SIZE_MB  REAL    NOT NULL DEFAULT 0.0
            )
        """

        const val SQL_DROP_TABLE = "DROP TABLE IF EXISTS $TABLE_NAME"

        // Index on (data_type, processing_node) — the two columns queried
        // most often by the "fastest method" and report queries.
        const val SQL_CREATE_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_type_node
            ON $TABLE_NAME ($COL_DATA_TYPE, $COL_PROCESSING_NODE)
        """
    }
}
