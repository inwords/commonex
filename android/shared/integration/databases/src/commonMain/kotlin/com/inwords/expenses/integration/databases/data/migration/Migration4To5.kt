package com.inwords.expenses.integration.databases.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.inwords.expenses.feature.events.data.db.entity.EventEntity
import com.inwords.expenses.feature.events.data.db.entity.PersonEntity
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseEntity

internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `${EventEntity.TABLE_NAME}` " +
                "ADD COLUMN `${EventEntity.ColumnNames.CLIENT_CREATE_ID}` TEXT NOT NULL DEFAULT ''"
        )
        connection.execSQL(
            "UPDATE `${EventEntity.TABLE_NAME}` " +
                "SET `${EventEntity.ColumnNames.CLIENT_CREATE_ID}` = CASE " +
                "WHEN `${EventEntity.ColumnNames.SERVER_ID}` IS NOT NULL " +
                "THEN 'server:' || `${EventEntity.ColumnNames.SERVER_ID}` " +
                "ELSE 'legacy:' || lower(hex(randomblob(16))) END"
        )

        connection.execSQL(
            "ALTER TABLE `${PersonEntity.TABLE_NAME}` " +
                "ADD COLUMN `${PersonEntity.ColumnNames.CLIENT_CREATE_ID}` TEXT NOT NULL DEFAULT ''"
        )
        connection.execSQL(
            "UPDATE `${PersonEntity.TABLE_NAME}` " +
                "SET `${PersonEntity.ColumnNames.CLIENT_CREATE_ID}` = CASE " +
                "WHEN `${PersonEntity.ColumnNames.SERVER_ID}` IS NOT NULL " +
                "THEN 'server:' || `${PersonEntity.ColumnNames.SERVER_ID}` " +
                "ELSE 'legacy:' || lower(hex(randomblob(16))) END"
        )

        connection.execSQL(
            "ALTER TABLE `${ExpenseEntity.TABLE_NAME}` " +
                "ADD COLUMN `${ExpenseEntity.ColumnNames.CLIENT_CREATE_ID}` TEXT NOT NULL DEFAULT ''"
        )
        connection.execSQL(
            "UPDATE `${ExpenseEntity.TABLE_NAME}` " +
                "SET `${ExpenseEntity.ColumnNames.CLIENT_CREATE_ID}` = CASE " +
                "WHEN `${ExpenseEntity.ColumnNames.SERVER_ID}` IS NOT NULL " +
                "THEN 'server:' || `${ExpenseEntity.ColumnNames.SERVER_ID}` " +
                "ELSE 'legacy:' || lower(hex(randomblob(16))) END"
        )
    }
}
