package com.example.offload

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Lightweight SQLiteOpenHelper — one singleton per Application via [getInstance].
 * Creates / upgrades the [OffloadLogContract.LogEntry] table.
 */
class OffloadLogDbHelper private constructor(context: Context) :
    SQLiteOpenHelper(
        context.applicationContext,
        OffloadLogContract.DATABASE_NAME,
        null,
        OffloadLogContract.DATABASE_VERSION
    ) {

    companion object {
        @Volatile
        private var INSTANCE: OffloadLogDbHelper? = null

        /**
         * Thread‑safe singleton accessor — avoids leaking Activity contexts and
         * guarantees a single DB connection pool across the app.
         */
        fun getInstance(context: Context): OffloadLogDbHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: OffloadLogDbHelper(context).also { INSTANCE = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(OffloadLogContract.LogEntry.SQL_CREATE_TABLE)
        db.execSQL(OffloadLogContract.LogEntry.SQL_CREATE_INDEX)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // For a v1→v2 migration you would ALTER TABLE here.
        // For now, destructive upgrade is fine for a dev/demo build.
        db.execSQL(OffloadLogContract.LogEntry.SQL_DROP_TABLE)
        onCreate(db)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // WAL mode gives us better concurrent read/write performance and
        // reduces blocking — important on a battery‑constrained device.
        db.enableWriteAheadLogging()
    }
}
