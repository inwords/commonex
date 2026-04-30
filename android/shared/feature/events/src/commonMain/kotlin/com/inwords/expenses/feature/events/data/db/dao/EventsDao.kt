package com.inwords.expenses.feature.events.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.inwords.expenses.feature.events.data.db.entity.EventCurrencyCrossRef
import com.inwords.expenses.feature.events.data.db.entity.EventEntity
import com.inwords.expenses.feature.events.data.db.entity.EventPersonCrossRef
import com.inwords.expenses.feature.events.data.db.entity.EventWithDetailsQuery
import com.inwords.expenses.feature.events.data.db.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventsDao {

    @Upsert
    suspend fun insert(eventEntity: EventEntity): Long

    @Upsert
    suspend fun insertPersonCrossRef(eventWithPersonCrossRefs: List<EventPersonCrossRef>)

    @Upsert
    suspend fun insertCurrencyCrossRef(eventWithCurrencyCrossRefs: List<EventCurrencyCrossRef>)

    @Query("UPDATE ${EventEntity.TABLE_NAME} SET ${EventEntity.ColumnNames.SERVER_ID} = :newServerId WHERE ${EventEntity.ColumnNames.ID} = :eventId")
    suspend fun update(eventId: Long, newServerId: String): Int

    @Query("SELECT * FROM ${EventEntity.TABLE_NAME}")
    fun queryAllEventsFlow(): Flow<List<EventEntity>>

    @Transaction
    @Query("SELECT * FROM ${EventEntity.TABLE_NAME} WHERE ${EventEntity.ColumnNames.ID} = :eventId")
    fun queryEventWithDetailsByIdFlow(eventId: Long): Flow<EventWithDetailsQuery?>

    @Transaction
    @Query("SELECT * FROM ${EventEntity.TABLE_NAME} WHERE ${EventEntity.ColumnNames.ID} = :eventId LIMIT 1")
    suspend fun queryEventWithDetailsById(eventId: Long): EventWithDetailsQuery?

    @Query("SELECT * FROM ${EventEntity.TABLE_NAME} WHERE ${EventEntity.ColumnNames.ID} = :eventId LIMIT 1")
    suspend fun queryEventById(eventId: Long): EventEntity?

    @Query("SELECT * FROM ${EventEntity.TABLE_NAME} WHERE ${EventEntity.ColumnNames.SERVER_ID} = :eventServerId LIMIT 1")
    suspend fun queryEventByServerId(eventServerId: String): EventEntity?

    @Transaction
    @Query("SELECT * FROM ${EventEntity.TABLE_NAME} WHERE ${EventEntity.ColumnNames.SERVER_ID} = :eventServerId LIMIT 1")
    suspend fun queryEventWithDetailsByServerId(eventServerId: String): EventWithDetailsQuery?

    @Query(
        "SELECT * FROM ${PersonEntity.TABLE_NAME} WHERE ${PersonEntity.ColumnNames.ID} IN " +
            "(SELECT ${PersonEntity.ColumnNames.ID} FROM ${EventPersonCrossRef.TABLE_NAME} WHERE ${EventEntity.ColumnNames.ID} = :eventId) " +
            "ORDER BY ${PersonEntity.ColumnNames.ID}"
    )
    suspend fun queryEventPersonsById(eventId: Long): List<PersonEntity>

    @Transaction
    @Query("DELETE FROM ${EventEntity.TABLE_NAME} WHERE ${EventEntity.ColumnNames.ID} = :eventId")
    suspend fun delete(eventId: Long): Int

}
