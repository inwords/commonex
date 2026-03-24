package com.inwords.expenses.core.analytics

fun initializePostHog(
    production: Boolean,
    postHogBridge: PostHogBridge,
) {
    val config = PostHogRuntimeConfig(
        apiKey = "phc_nFOH7z2wxhqrdBk2HH0Pbk4PDMv75jDY1e1AEwdxQJu",
        host = "https://eu.i.posthog.com",
        captureApplicationLifecycleEvents = true,
        captureScreenViews = false,
        debug = !production,
        optOut = !production,
    )

    postHogBridge.setupPostHog(config)
}
