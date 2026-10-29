package com.inofarm.jogja.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class FinanceDatabase(context: Context, name: String = "inofarm_finance_v2.db") : SQLiteOpenHelper(context.applicationContext, name, null, VERSION) {
    init { setWriteAheadLoggingEnabled(true) }
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) { createVersionOne(db); migrateVersionTwo(db) }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        require(oldVersion in 1..VERSION && newVersion == VERSION) { "Versi database tidak didukung. Data dipertahankan." }
        if(oldVersion < 2) migrateVersionTwo(db)
    }
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("Downgrade database ditahan untuk melindungi draf.") }
    companion object {
        const val VERSION = 2
        /** Kept executable for the migration test. No destructive recreate fallback. */
        fun createVersionOne(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE scope_metadata(scope TEXT PRIMARY KEY, epoch TEXT NOT NULL, role TEXT NOT NULL, visible_greenhouses TEXT NOT NULL, last_synced_at INTEGER)""")
            db.execSQL("CREATE TABLE scope_access(scope TEXT NOT NULL, greenhouse_id TEXT NOT NULL, PRIMARY KEY(scope,greenhouse_id), FOREIGN KEY(scope) REFERENCES scope_metadata(scope))")
            db.execSQL("""CREATE TABLE local_records(scope TEXT NOT NULL, entity_id TEXT NOT NULL, kind TEXT NOT NULL, greenhouse_id TEXT NOT NULL, revision INTEGER NOT NULL,
                base_version INTEGER, server_version INTEGER, body TEXT NOT NULL, server_body TEXT, latest_operation_id TEXT, sync_state TEXT NOT NULL, error TEXT, updated_at INTEGER NOT NULL,
                PRIMARY KEY(scope,entity_id), FOREIGN KEY(scope) REFERENCES scope_metadata(scope))""")
            db.execSQL("""CREATE TABLE outbox(scope TEXT NOT NULL, operation_id TEXT NOT NULL, entity_id TEXT NOT NULL, local_revision INTEGER NOT NULL, envelope TEXT NOT NULL,
                canonical_hash TEXT NOT NULL, state TEXT NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at INTEGER NOT NULL DEFAULT 0, error TEXT, created_at INTEGER NOT NULL,
                PRIMARY KEY(scope,operation_id), FOREIGN KEY(scope,entity_id) REFERENCES local_records(scope,entity_id))""")
            db.execSQL("CREATE INDEX outbox_retry ON outbox(scope,state,next_attempt_at,created_at)")
            db.execSQL("""CREATE TABLE cached_records(scope TEXT NOT NULL, kind TEXT NOT NULL, entity_id TEXT NOT NULL, greenhouse_id TEXT NOT NULL, version INTEGER NOT NULL,
                tombstone INTEGER NOT NULL, body TEXT NOT NULL, PRIMARY KEY(scope,kind,entity_id), FOREIGN KEY(scope) REFERENCES scope_metadata(scope))""")
            db.execSQL("CREATE INDEX cached_context ON cached_records(scope,kind,greenhouse_id)")
        }
        private fun migrateVersionTwo(db: SQLiteDatabase) { db.execSQL("ALTER TABLE scope_metadata ADD COLUMN pull_cursor TEXT") }
    }
}
