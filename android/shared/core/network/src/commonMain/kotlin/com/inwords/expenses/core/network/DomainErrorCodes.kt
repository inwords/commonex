package com.inwords.expenses.core.network

object DomainErrorCodes {

    const val INVALID_TOKEN = "B4008"
    const val TOKEN_EXPIRED = "B4009"
    const val EXPENSE_REFERENCE_NOT_FOUND = "B4012"
    const val EXPENSE_ALREADY_REVERTED = "B4013"
    const val EXPENSE_CORRECTION_CONFLICT = "B4014"

    val permanentExpenseCorrectionErrorCodes = setOf(
        EXPENSE_REFERENCE_NOT_FOUND,
        EXPENSE_ALREADY_REVERTED,
        EXPENSE_CORRECTION_CONFLICT,
    )

}
