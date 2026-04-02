package com.inwords.expenses.integration.databases.data

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURL

internal actual class RoomDatabaseBuilderFactory {

    @OptIn(ExperimentalForeignApi::class)
    actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
        val fileManager = NSFileManager.defaultManager
        val documentDirectory = fileManager.URLsForDirectory(
            directory = NSDocumentDirectory,
            inDomains = NSUserDomainMask,
        ).first() as NSURL
        val dbDirectory = documentDirectory.path!! + "/databases"
        fileManager.createDirectoryAtPath(dbDirectory, withIntermediateDirectories = true, attributes = null, error = null)
        val dbFilePath = "$dbDirectory/app_db.db"
        return Room.databaseBuilder<AppDatabase>(
            name = dbFilePath,
        )
    }
}
