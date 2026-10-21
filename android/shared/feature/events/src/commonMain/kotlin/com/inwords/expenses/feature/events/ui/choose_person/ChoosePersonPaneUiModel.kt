package com.inwords.expenses.feature.events.ui.choose_person

import kotlinx.collections.immutable.ImmutableList

internal data class ChoosePersonPaneUiModel(
    val eventId: Long,
    val eventName: String,
    val persons: ImmutableList<PersonUiModel>,
) {

    internal data class PersonUiModel(
        val id: Long,
        val name: String,
        val selected: Boolean,
    ) {

        val initial: String = name.take(1).uppercase()
    }
}
