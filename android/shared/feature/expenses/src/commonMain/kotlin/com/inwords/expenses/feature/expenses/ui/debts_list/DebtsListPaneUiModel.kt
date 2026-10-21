package com.inwords.expenses.feature.expenses.ui.debts_list

import com.inwords.expenses.feature.expenses.ui.common.DebtShortUiModel
import kotlinx.collections.immutable.ImmutableList

internal data class DebtsListPaneUiModel(
    val eventName: String,
    val primaryCurrencyCode: String,
    val debtors: ImmutableList<DebtorUiModel>,
) {

    data class DebtorUiModel(
        val person: PersonUiModel,
        val creditors: ImmutableList<DebtShortUiModel>,
    )

    data class PersonUiModel(
        val personId: Long,
        val personName: String,
    ) {

        val personInitial: String = personName.take(1).uppercase()
    }
}
