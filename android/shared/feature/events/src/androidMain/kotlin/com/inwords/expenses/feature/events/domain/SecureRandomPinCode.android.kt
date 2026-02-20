package com.inwords.expenses.feature.events.domain

import java.security.SecureRandom

internal actual object SecureRandomPinCode {

    actual fun nextPinCode(length: Int): String {
        require(length > 0)

        val secureRandom = SecureRandom()
        return buildString(length) {
            repeat(length) {
                append(secureRandom.nextInt(10))
            }
        }
    }
}
