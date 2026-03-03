package com.inwords.expenses.feature.expenses.ui.list.bottom_sheet.item

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.integer.BigInteger

private const val EXCHANGE_RATE_SCALE = 2L

internal fun calculateExchangeRateValue(
    totalOriginalAmount: BigDecimal,
    totalExchangedAmount: BigDecimal,
): String? {
    val originalAmount = totalOriginalAmount.abs()
    if (originalAmount == BigDecimal.ZERO) {
        return null
    }

    val totalExchangedAmountAbs = totalExchangedAmount.abs()
    val roundedRate = totalExchangedAmountAbs
        .divide(
            other = originalAmount,
            decimalMode = DecimalMode(
                decimalPrecision = estimateDivisionPrecision(
                    dividend = totalExchangedAmountAbs,
                    divisor = originalAmount,
                ),
                roundingMode = RoundingMode.ROUND_HALF_TO_EVEN,
            ),
        )
        .roundToDigitPositionAfterDecimalPoint(
            digitPosition = EXCHANGE_RATE_SCALE,
            roundingMode = RoundingMode.ROUND_HALF_TO_EVEN,
        )
    return roundedRate.toFixedScaleString(scale = EXCHANGE_RATE_SCALE.toInt())
}

private fun BigDecimal.toFixedScaleString(scale: Int): String {
    val multiplier = BigDecimal.TEN.pow(scale)
    val scaledAbs = (abs() * multiplier).toBigInteger()
    val scaledAbsStr = scaledAbs.toString()
    val sign = if (this < BigDecimal.ZERO && scaledAbs != BigInteger.ZERO) "-" else ""

    if (scale == 0) {
        return sign + scaledAbsStr
    }

    val whole = if (scaledAbsStr.length > scale) scaledAbsStr.dropLast(scale) else "0"
    val fraction = scaledAbsStr.takeLast(scale).padStart(scale, '0')
    return "$sign$whole.$fraction"
}

private fun estimateDivisionPrecision(dividend: BigDecimal, divisor: BigDecimal): Long {
    val integerDigitsEstimate = (dividend.exponent - divisor.exponent + 1).coerceAtLeast(1)
    val roundingGuardDigits = 2L
    return integerDigitsEstimate + EXCHANGE_RATE_SCALE + roundingGuardDigits
}
