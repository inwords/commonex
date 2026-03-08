package com.inwords.expenses.core.network

/**
 * Builds a single, centralized User-Agent string for HTTP clients so Android and iOS stay in sync.
 */
internal fun buildUserAgent(versionCode: Int, platform: String, production: Boolean): String {
    val buildType = if (production) "r" else "d"
    return "CommonEx/$versionCode ($platform/$buildType)"
}
