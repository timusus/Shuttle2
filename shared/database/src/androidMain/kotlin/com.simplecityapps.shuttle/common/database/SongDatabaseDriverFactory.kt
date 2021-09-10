package com.simplecityapps.shuttle.common.database

import android.content.Context
import com.simplecityapps.shuttle.database.SongDatabase
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver

@Suppress("FunctionName") // FactoryFunction
fun SongDatabaseDriver(context: Context): SqlDriver =
    AndroidSqliteDriver(
        schema = SongDatabase.Schema,
        context = context,
        name = "SongDatabase.db"
    )
