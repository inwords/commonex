package com.inwords.expenses.core.utils

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode

fun String.toBigDecimalOrNull(): BigDecimal? {
    return try {
        BigDecimal.parseString(this)
    } catch (e: NumberFormatException) {
        null
    } catch (e: ArithmeticException) {
        null
    } catch (e: IndexOutOfBoundsException) { // TODO broken library behavior
        null
    }
}

/**
 * Formats this BigDecimal as a plain decimal string (no scientific notation).
 * Whole numbers are shown without trailing zeros (e.g. "10"); decimals use the given scale.
 */
fun BigDecimal.toPlainDecimalString(scale: Long = 2): String {
    val scaled = this.scale(scale)
    return if ((scaled * BigDecimal.TEN % 10).significand == BigDecimal.ZERO.significand) {
        scaled.toBigInteger().toString()
    } else {
        scaled.toStringExpanded()
    }
}

fun BigDecimal.divide(other: BigDecimal, scale: Long): BigDecimal {
    return divide(
        other = other,
        decimalMode = DecimalMode(
            decimalPrecision = this.exponent - other.exponent + 1 + scale,
            scale = scale,
            roundingMode = RoundingMode.ROUND_HALF_TO_EVEN
        )
    )
}
