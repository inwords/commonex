import Foundation
import PostHog
import sharedIntegrationBase

final class IOSPostHogBridge: NSObject, PostHogBridge {

    func setupPostHog(config: PostHogRuntimeConfig) {
        let postHogConfig = PostHogConfig(apiKey: config.apiKey, host: config.host)
        postHogConfig.captureApplicationLifecycleEvents = config.captureApplicationLifecycleEvents
        postHogConfig.captureScreenViews = config.captureScreenViews
        postHogConfig.enableSwizzling = false
        postHogConfig.debug = config.debug
        postHogConfig.optOut = config.optOut

        PostHogSDK.shared.setup(postHogConfig)
    }
}
