package com.inwords.expenses.integration.databases.api

import com.inwords.expenses.integration.databases.data.RoomDatabaseBuilderFactory

actual class DatabasesComponentFactory actual constructor(private val deps: Deps) {
    actual interface Deps

    actual fun create(): DatabasesComponent {
        return DatabasesComponent(RoomDatabaseBuilderFactory())
    }

}
