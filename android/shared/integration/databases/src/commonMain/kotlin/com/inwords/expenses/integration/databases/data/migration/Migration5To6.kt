package com.inwords.expenses.integration.databases.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseEntity

internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `${ExpenseEntity.TABLE_NAME}` " +
                "ADD COLUMN `${ExpenseEntity.ColumnNames.REVERTS_EXPENSE_ID}` INTEGER DEFAULT NULL"
        )
        connection.execSQL(
            "ALTER TABLE `${ExpenseEntity.TABLE_NAME}` " +
                "ADD COLUMN `${ExpenseEntity.ColumnNames.REPLACES_EXPENSE_ID}` INTEGER DEFAULT NULL"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_expense_reverts_expense_id` " +
                "ON `${ExpenseEntity.TABLE_NAME}` (`${ExpenseEntity.ColumnNames.REVERTS_EXPENSE_ID}`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_expense_replaces_expense_id` " +
                "ON `${ExpenseEntity.TABLE_NAME}` (`${ExpenseEntity.ColumnNames.REPLACES_EXPENSE_ID}`)",
        )
    }
}
