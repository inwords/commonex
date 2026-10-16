package com.inwords.expenses.feature.events.ui.create

import kotlinx.collections.immutable.ImmutableList

internal data class CreateEventPaneUiModel(
    val eventName: String,
    val currencies: ImmutableList<CurrencyInfoUiModel>,
) {

    data class CurrencyInfoUiModel(
        val currencyName: String,
        val currencyCode: String,
        val selected: Boolean,
    )
}
