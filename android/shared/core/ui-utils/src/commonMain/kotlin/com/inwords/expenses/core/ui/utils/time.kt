package com.inwords.expenses.core.ui.utils

import androidx.compose.ui.text.intl.Locale
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private val MonthNames.Companion.RUSSIAN_FULL by lazy(mode = LazyThreadSafetyMode.PUBLICATION) {
    MonthNames(
        january = "января",
        february = "февраля",
        march = "марта",
        april = "апреля",
        may = "мая",
        june = "июня",
        july = "июля",
        august = "августа",
        september = "сентября",
        october = "октября",
        november = "ноября",
        december = "декабря",
    )
}

private val fullDateFormats = LazyFormatMap(
    defaultLocale = "en",
    keys = setOf("en", "ru"),
    keyFallbackMap = mapOf(
        "by" to "ru",
    ),
    createValueForKey = { langCode ->
        LocalDate.Format {
            day(padding = Padding.ZERO)
            char(' ')
            monthName(
                if (langCode == "ru") {
                    MonthNames.RUSSIAN_FULL
                } else {
                    MonthNames.ENGLISH_FULL
                }
            )
            char(' ')
            year(padding = Padding.ZERO)
        }
    },
    lazyThreadSafetyMode = LazyThreadSafetyMode.PUBLICATION
)

fun getFullDateFormat(locale: Locale = Locale.current): DateTimeFormat<LocalDate> {
    return fullDateFormats.getValue(locale.language)
}

private val defaultDateTimeFormats = LazyFormatMap(
    defaultLocale = "en",
    keys = setOf("en", "ru"),
    keyFallbackMap = mapOf(
        "by" to "ru",
    ),
    createValueForKey = { // TODO better formats per locale
        LocalDateTime.Format {
            date(
                LocalDate.Format {
                    day(padding = Padding.ZERO)
                    char('.')
                    monthNumber()
                    char('.')
                    yearTwoDigits(2000)
                }
            )
            char(' ')
            time(
                LocalTime.Format {
                    hour(); char(':'); minute()
                }
            )
        }
    },
    lazyThreadSafetyMode = LazyThreadSafetyMode.PUBLICATION
)

fun getDefaultDateTimeFormat(locale: Locale = Locale.current): DateTimeFormat<LocalDateTime> {
    return defaultDateTimeFormats.getValue(locale.language)
}

private val shortDateFormats = LazyFormatMap(
    defaultLocale = "en",
    keys = setOf("en", "ru"),
    keyFallbackMap = mapOf(
        "by" to "ru",
    ),
    createValueForKey = {
        LocalDate.Format {
            day()
            char('.')
            monthNumber()
        }
    },
    lazyThreadSafetyMode = LazyThreadSafetyMode.PUBLICATION
)

fun getShortDateFormat(locale: Locale = Locale.current): DateTimeFormat<LocalDate> {
    return shortDateFormats.getValue(locale.language)
}

private val shortDateWithYearFormats = LazyFormatMap(
    defaultLocale = "en",
    keys = setOf("en", "ru"),
    keyFallbackMap = mapOf(
        "by" to "ru",
    ),
    createValueForKey = {
        LocalDate.Format {
            day()
            char('.')
            monthNumber()
            char('.')
            yearTwoDigits(2000)
        }
    },
    lazyThreadSafetyMode = LazyThreadSafetyMode.PUBLICATION
)

fun getShortDateWithYearFormat(locale: Locale = Locale.current): DateTimeFormat<LocalDate> {
    return shortDateWithYearFormats.getValue(locale.language)
}

fun LocalDate.formatRelativeShortDate(
    currentLocalDate: LocalDate,
    todayLabel: String,
    yesterdayLabel: String,
    locale: Locale = Locale.current,
): String {
    return when {
        this == currentLocalDate -> todayLabel
        this == currentLocalDate.minus(1, DateTimeUnit.DAY) -> yesterdayLabel
        this.year == currentLocalDate.year -> format(getShortDateFormat(locale))
        else -> format(getShortDateWithYearFormat(locale))
    }
}

private val timeFormats = LazyFormatMap(
    defaultLocale = "en",
    keys = setOf("en", "ru"),
    keyFallbackMap = mapOf(
        "by" to "ru",
    ),
    createValueForKey = {
        LocalTime.Format {
            hour()
            char(':')
            minute()
        }
    },
    lazyThreadSafetyMode = LazyThreadSafetyMode.PUBLICATION
)

fun getTimeFormat(locale: Locale = Locale.current): DateTimeFormat<LocalTime> {
    return timeFormats.getValue(locale.language)
}

fun Instant.formatLocalDateTime(format: DateTimeFormat<LocalDateTime>): String {
    return this.toLocalDateTime(TimeZone.currentSystemDefault()).format(format)
}

fun Instant.formatLocalDate(format: DateTimeFormat<LocalDate>): String {
    return this.toLocalDateTime(TimeZone.currentSystemDefault()).date.format(format)
}

fun Instant.formatLocalTime(format: DateTimeFormat<LocalTime>): String {
    return this.toLocalDateTime(TimeZone.currentSystemDefault()).time.format(format)
}
