package com.simplecityapps.shuttle.common.database

import com.simplecityapps.shuttle.database.SongDatabase
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.drivers.native.NativeSqliteDriver

@Suppress("FunctionName") // Factory function
fun SongDatabaseDriver(): SqlDriver =
    NativeSqliteDriver(SongDatabase.Schema, "SongDatabase.db")
