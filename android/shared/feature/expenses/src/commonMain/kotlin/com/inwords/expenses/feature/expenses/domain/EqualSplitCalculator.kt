package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.core.utils.divide
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal

internal object EqualSplitCalculator {

    fun calculateDraftAmount(
        amount: BigDecimal,
        selectedSubjectPersonsSize: Int,
    ): BigDecimal {
        return calculateAmount(
            amount = amount,
            selectedSubjectPersonsSize = selectedSubjectPersonsSize,
            scale = 2,
        )
    }

    fun calculateStoredAmount(
        amount: BigDecimal,
        selectedSubjectPersonsSize: Int,
    ): BigDecimal {
        return calculateAmount(
            amount = amount,
            selectedSubjectPersonsSize = selectedSubjectPersonsSize,
            scale = 3,
        )
    }

    private fun calculateAmount(
        amount: BigDecimal,
        selectedSubjectPersonsSize: Int,
        scale: Long,
    ): BigDecimal {
        return amount.divide(
            other = selectedSubjectPersonsSize.coerceAtLeast(1).toBigDecimal(),
            scale = scale,
        )
    }
}
