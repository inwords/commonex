package com.inwords.expenses.core.analytics

data class PostHogRuntimeConfig(
    val apiKey: String,
    val host: String,
    val captureApplicationLifecycleEvents: Boolean,
    val captureScreenViews: Boolean,
    val debug: Boolean,
    val optOut: Boolean,
)
