package com.inwords.expenses.feature.events.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.inwords.expenses.core.storage.utils.type_converter.BigIntegerConverter
import com.ionspin.kotlin.bignum.integer.BigInteger

@Entity(tableName = CurrencyEntity.TABLE_NAME)
@TypeConverters(BigIntegerConverter::class)
data class CurrencyEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(ColumnNames.ID)
    val currencyId: Long,

    @ColumnInfo(ColumnNames.SERVER_ID)
    val currencyServerId: String?,

    @ColumnInfo(ColumnNames.CODE)
    val code: String,

    @ColumnInfo(ColumnNames.NAME)
    val name: String,

    @ColumnInfo(ColumnNames.RATE_UNSCALED)
    val rateUnscaled: BigInteger,

    @ColumnInfo(ColumnNames.RATE_SCALE)
    val rateScale: Long,
) {
    companion object {

        const val TABLE_NAME = "currency"
    }

    object ColumnNames {

        const val ID = "currency_id"
        const val SERVER_ID = "currency_server_id"
        const val CODE = "code"
        const val NAME = "name"
        const val RATE_UNSCALED = "rate_unscaled"
        const val RATE_SCALE = "rate_scale"
    }
}
