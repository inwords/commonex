package com.inwords.expenses.feature.share.api

import android.content.Context

actual class ShareComponentFactory actual constructor(private val deps: Deps) {

    actual interface Deps {
        val context: Context
    }

    actual fun create(): ShareComponent {
        return ShareComponent(lazy { ShareManager(deps.context) })
    }
}
