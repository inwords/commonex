package com.inwords.expenses.core.utils

import com.ionspin.kotlin.bignum.decimal.BigDecimal

/**
 * Standard money precision for amounts shown to users and stored as expense amounts.
 */
const val amountScale: Long = 2

/**
 * Standard precision for backend-driven currency rates cached on mobile.
 */
const val currencyRateScale: Long = 4

fun BigDecimal.normalizeAmount(): BigDecimal = scale(amountScale)

fun BigDecimal.normalizeCurrencyRate(): BigDecimal = scale(currencyRateScale)
