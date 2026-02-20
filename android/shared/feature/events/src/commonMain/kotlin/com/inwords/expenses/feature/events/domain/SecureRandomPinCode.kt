package com.inwords.expenses.feature.events.domain

internal expect object SecureRandomPinCode {

    fun nextPinCode(length: Int): String
}
