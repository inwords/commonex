package com.inwords.expenses.feature.events.domain

internal actual object SecureRandomPinCode {

    actual fun nextPinCode(length: Int): String {
        require(length > 0)

        return buildString(length) {
            repeat(length) {
                append((0..9).random())
            }
        }
    }
}
