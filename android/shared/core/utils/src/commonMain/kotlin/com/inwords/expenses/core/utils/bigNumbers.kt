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
