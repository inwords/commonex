package com.inwords.expenses.core.analytics

interface PostHogBridge {

    fun setupPostHog(config: PostHogRuntimeConfig)
}
