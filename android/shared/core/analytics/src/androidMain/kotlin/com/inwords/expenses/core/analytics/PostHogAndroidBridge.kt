package com.inwords.expenses.core.analytics

import android.content.Context
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

class PostHogAndroidBridge(
    private val appContext: Context
) : PostHogBridge {

    override fun setupPostHog(config: PostHogRuntimeConfig) {
        val postHogConfig = PostHogAndroidConfig(
            apiKey = config.apiKey,
            host = config.host,
        ).apply {
            captureApplicationLifecycleEvents = config.captureApplicationLifecycleEvents
            captureScreenViews = config.captureScreenViews
            debug = config.debug
            optOut = config.optOut
        }

        PostHogAndroid.setup(appContext, postHogConfig)
    }
}
