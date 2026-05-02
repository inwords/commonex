package com.inwords.expenses.core.ui.utils

import androidx.compose.ui.text.intl.Locale

private const val CommonExBaseUrl = "https://commonex.ru"
private const val DefaultLanguageCode = "en"
private const val RussianLanguageCode = "ru"

fun privacyPolicyUrl(locale: Locale = Locale.current): String {
    return legalDocumentUrl(
        languageCode = locale.language,
        defaultPath = "privacy.html",
        russianPath = "policy-ru.html",
    )
}

fun termsOfUseUrl(locale: Locale = Locale.current): String {
    return legalDocumentUrl(
        languageCode = locale.language,
        defaultPath = "terms.html",
        russianPath = "terms-ru.html",
    )
}

private fun legalDocumentUrl(languageCode: String, defaultPath: String, russianPath: String): String {
    val path = if (normalizeLanguageCode(languageCode) == RussianLanguageCode) {
        russianPath
    } else {
        defaultPath
    }

    return "$CommonExBaseUrl/$path"
}

private fun normalizeLanguageCode(languageCode: String): String {
    return when (languageCode.lowercase()) {
        "by" -> RussianLanguageCode
        RussianLanguageCode -> RussianLanguageCode
        else -> DefaultLanguageCode
    }
}
