package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_40_41 =
    object : Migration(40, 41) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Create sync_operations table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_operations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    playlist_id INTEGER NOT NULL,
                    external_id TEXT,
                    media_provider_type TEXT NOT NULL,
                    operation_type TEXT NOT NULL,
                    operation_data TEXT NOT NULL,
                    status TEXT NOT NULL,
                    priority INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    last_attempt_at INTEGER,
                    retry_count INTEGER NOT NULL DEFAULT 0,
                    max_retries INTEGER NOT NULL DEFAULT 3,
                    error_message TEXT,
                    FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )

            // Create indexes for sync_operations
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_operations_playlist_id ON sync_operations(playlist_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_operations_status ON sync_operations(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_operations_priority_created_at ON sync_operations(priority, created_at)")

            // Create playlist_sync_state table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS playlist_sync_state (
                    playlist_id INTEGER PRIMARY KEY NOT NULL,
                    last_synced_at INTEGER,
                    local_modified_at INTEGER NOT NULL,
                    remote_modified_at INTEGER,
                    sync_status TEXT NOT NULL,
                    conflict_detected INTEGER NOT NULL DEFAULT 0,
                    local_content_hash TEXT NOT NULL,
                    remote_content_hash TEXT,
                    FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
        }
    }
