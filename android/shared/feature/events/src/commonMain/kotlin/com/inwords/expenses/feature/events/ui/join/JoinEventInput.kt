package com.inwords.expenses.feature.events.ui.join

private val eventIdRegex = "[0-9A-HJKMNP-TV-Z]".toRegex()

internal fun String.filteredEventId(): String {
    return uppercase().filter { it.toString().matches(eventIdRegex) }
}

internal fun String.filteredPinCode(): String {
    return filter { it.isDigit() }
}

internal fun String.filteredToken(): String {
    return filter { it.isLetterOrDigit() }
}
