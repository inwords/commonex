package com.inwords.expenses.core.ui.design.selection

data class SingleChoiceOption<T>(
    val key: String,
    val text: String,
    val supportingText: String?,
    val selected: Boolean,
    val payload: T,
    val testTag: String? = null,
)
