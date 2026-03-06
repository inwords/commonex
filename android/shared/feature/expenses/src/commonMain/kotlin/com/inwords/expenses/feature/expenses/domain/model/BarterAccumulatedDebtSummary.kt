package com.inwords.expenses.feature.expenses.domain.model

import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Person
import com.ionspin.kotlin.bignum.decimal.BigDecimal

data class BarterAccumulatedDebtSummary(
    val debtor: Person,
    val creditor: Person,
    val amount: BigDecimal,
    val currency: Currency,
)
