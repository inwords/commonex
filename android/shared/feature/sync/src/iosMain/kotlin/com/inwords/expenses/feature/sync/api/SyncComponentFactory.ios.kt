package com.inwords.expenses.feature.sync.api

import com.inwords.expenses.feature.sync.data.EventsSyncManagerFactory

actual class SyncComponentFactory(private val deps: Deps) {

    actual interface Deps : SyncComponentFactoryCommonDeps

    actual fun create(): SyncComponent {
        val syncManagerFactory = EventsSyncManagerFactory()
        return SyncComponent(eventsSyncManagerFactory = syncManagerFactory, deps = deps)
    }

}
