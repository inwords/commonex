package com.inwords.expenses.feature.events.domain

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

internal actual object SecureRandomPinCode {

    @OptIn(ExperimentalForeignApi::class)
    actual fun nextPinCode(length: Int): String {
        require(length > 0)

        val randomBytes = ByteArray(length)
        randomBytes.usePinned { pinned ->
            val status = SecRandomCopyBytes(kSecRandomDefault, length.toULong(), pinned.addressOf(0))
            check(status == 0) { "Unable to generate secure random pin code" }
        }

        return buildString(length) {
            randomBytes.forEach { value ->
                append(((value.toInt() and 0xFF) % 10))
            }
        }
    }
}
