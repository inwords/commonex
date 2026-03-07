package com.inwords.expenses.integration.databases.api

import android.content.Context
import com.inwords.expenses.integration.databases.data.RoomDatabaseBuilderFactory

actual class DatabasesComponentFactory actual constructor(private val deps: Deps) {

    actual interface Deps {
        val context: Context
    }

    actual fun create(): DatabasesComponent {
        return DatabasesComponent(RoomDatabaseBuilderFactory(deps.context))
    }
}
