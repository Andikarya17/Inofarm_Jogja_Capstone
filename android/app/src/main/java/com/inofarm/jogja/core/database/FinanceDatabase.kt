package com.inofarm.jogja.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class FinanceDatabase(context: Context, name: String = "inofarm_finance_v2.db") : SQLiteOpenHelper(context.applicationContext, name, null, VERSION) {
    init { setWriteAheadLoggingEnabled(true) }
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) { createVersionOne(db); migrateVersionTwo(db); migrateVersionThree(db); migrateVersionFour(db);migrateVersionFive(db);migrateVersionSix(db) }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        require(oldVersion in 1..VERSION && newVersion == VERSION) { "Versi database tidak didukung. Data dipertahankan." }
        if(oldVersion < 2) migrateVersionTwo(db)
        if(oldVersion < 3) migrateVersionThree(db)
        if(oldVersion < 4) migrateVersionFour(db)
        if(oldVersion < 5) migrateVersionFive(db)
        if(oldVersion < 6) migrateVersionSix(db)
    }
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("Downgrade database ditahan untuk melindungi draf.") }
    companion object {
        const val VERSION = 6
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
        private fun migrateVersionThree(db: SQLiteDatabase) {
            for(column in listOf("occurred_date","activity_type","group_id","record_state")) db.execSQL("ALTER TABLE local_records ADD COLUMN $column TEXT")
            db.execSQL("ALTER TABLE local_records ADD COLUMN indexed INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX local_history_filter ON local_records(scope,kind,group_id,occurred_date)")
            db.execSQL("""CREATE TABLE local_record_revisions(scope TEXT NOT NULL,entity_id TEXT NOT NULL,revision INTEGER NOT NULL,
                kind TEXT NOT NULL,greenhouse_id TEXT NOT NULL,body TEXT NOT NULL,server_body TEXT,base_version INTEGER,server_version INTEGER,
                resolution TEXT NOT NULL,saved_at INTEGER NOT NULL,PRIMARY KEY(scope,entity_id,revision),
                FOREIGN KEY(scope,entity_id) REFERENCES local_records(scope,entity_id))""")
        }
        private fun migrateVersionFour(db:SQLiteDatabase) {
            db.execSQL("ALTER TABLE local_records ADD COLUMN occurred_millis INTEGER")
            db.execSQL("UPDATE local_records SET indexed=0")
        }
        private fun migrateVersionFive(db:SQLiteDatabase) {
            db.execSQL("ALTER TABLE local_records ADD COLUMN season_id TEXT")
            db.execSQL("ALTER TABLE local_records ADD COLUMN expense_id TEXT")
            db.execSQL("CREATE INDEX local_cost_parent ON local_records(scope,kind,expense_id,season_id,record_state)")
            db.execSQL("UPDATE local_records SET indexed=0")
        }
        private fun migrateVersionSix(db:SQLiteDatabase) {
            db.execSQL("ALTER TABLE outbox ADD COLUMN blob_id TEXT")
            db.execSQL("ALTER TABLE local_records ADD COLUMN owner_id TEXT")
            db.execSQL("CREATE INDEX local_attachment_owner ON local_records(scope,kind,owner_id,updated_at)")
            db.execSQL("UPDATE local_records SET indexed=0")
            db.execSQL("""CREATE TABLE local_blobs(scope TEXT NOT NULL,blob_id TEXT NOT NULL,entity_id TEXT NOT NULL,kind TEXT NOT NULL,
                greenhouse_id TEXT NOT NULL,state TEXT NOT NULL,body TEXT NOT NULL,updated_at INTEGER NOT NULL,
                PRIMARY KEY(scope,blob_id),FOREIGN KEY(scope) REFERENCES scope_metadata(scope))""")
            db.execSQL("CREATE INDEX local_blob_owner ON local_blobs(scope,entity_id,state,updated_at)")
        }
    }
}
