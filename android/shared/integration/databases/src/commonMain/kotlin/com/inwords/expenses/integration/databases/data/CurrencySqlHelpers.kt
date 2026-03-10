package com.inwords.expenses.integration.databases.data

import com.inwords.expenses.feature.events.domain.model.Currency
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.util.toTwosComplementByteArray

internal val Currency.rateUnscaled: BigInteger
    get() = rate.significand

internal val Currency.rateScale: Long
    get() = rate.exponent

internal val Currency.rateUnscaledSqlLiteral: String
    get() = rateUnscaled.toSqlBlobLiteral()

private fun BigInteger.toSqlBlobLiteral(): String {
    return buildString {
        append("X'")
        toTwosComplementByteArray().forEach { byte ->
            append(byte.toUByte().toString(radix = 16).padStart(length = 2, padChar = '0').uppercase())
        }
        append("'")
    }
}
