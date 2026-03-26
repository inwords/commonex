package com.inwords.expenses.integration.databases.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseEntity

internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `${ExpenseEntity.TABLE_NAME}` ADD COLUMN `${ExpenseEntity.ColumnNames.IS_CUSTOM_RATE}` INTEGER NOT NULL DEFAULT 0"
        )
    }
}
