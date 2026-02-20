package com.inwords.expenses.integration.databases.data

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal class RoomOnOpenPragmasCallback : RoomDatabase.Callback() {
    override fun onOpen(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA synchronous=${DatabasePragmas.SYNCHRONOUS}")
    }
}
