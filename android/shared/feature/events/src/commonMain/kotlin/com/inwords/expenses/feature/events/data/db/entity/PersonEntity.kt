package com.inwords.expenses.feature.events.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = PersonEntity.TABLE_NAME)
data class PersonEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(ColumnNames.ID)
    val personId: Long = 0L,

    @ColumnInfo(ColumnNames.SERVER_ID)
    val personServerId: String?,

    @ColumnInfo(ColumnNames.CLIENT_CREATE_ID)
    val clientCreateId: String,

    @ColumnInfo(ColumnNames.NAME)
    val name: String,
) {
    companion object {

        const val TABLE_NAME = "person"
    }

    object ColumnNames {

        const val ID = "person_id"
        const val SERVER_ID = "person_server_id"
        const val CLIENT_CREATE_ID = "client_create_id"
        const val NAME = "name"
    }
}
