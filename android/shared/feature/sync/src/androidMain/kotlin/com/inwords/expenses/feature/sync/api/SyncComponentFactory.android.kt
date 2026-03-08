package com.inwords.expenses.feature.sync.api

import android.content.Context
import com.inwords.expenses.feature.sync.data.EventsSyncManagerFactory

actual class SyncComponentFactory(private val deps: Deps) {

    actual interface Deps : SyncComponentFactoryCommonDeps {
        val context: Context
    }

    actual fun create(): SyncComponent {
        val syncManagerFactory = EventsSyncManagerFactory(deps.context)
        return SyncComponent(eventsSyncManagerFactory = syncManagerFactory, deps = deps)
    }

}
