package com.inwords.expenses.feature.events.ui.create

import kotlinx.collections.immutable.ImmutableList

internal data class CreateEventPaneUiModel(
    val eventName: String,
    val ownerName: String,
    val persons: ImmutableList<String>,
    val currencies: ImmutableList<CurrencyInfoUiModel>,
    val isCreating: Boolean,
) {

    val isConfirmEnabled: Boolean by lazy {
        eventName.isNotBlank() && ownerName.isNotBlank() && currencies.any { it.selected } && !isCreating
    }

    data class CurrencyInfoUiModel(
        val currencyName: String,
        val currencyCode: String,
        val selected: Boolean,
    )
}
