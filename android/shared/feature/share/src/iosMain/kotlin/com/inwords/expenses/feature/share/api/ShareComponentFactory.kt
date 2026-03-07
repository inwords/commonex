package com.inwords.expenses.feature.share.api

actual class ShareComponentFactory actual constructor(deps: Deps) {

    actual interface Deps

    actual fun create(): ShareComponent {
        return ShareComponent(lazy { ShareManager() })
    }
}
