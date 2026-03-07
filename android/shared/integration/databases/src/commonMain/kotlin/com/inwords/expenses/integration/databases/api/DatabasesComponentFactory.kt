package com.inwords.expenses.integration.databases.api

expect class DatabasesComponentFactory(deps: Deps) {

    interface Deps

    fun create(): DatabasesComponent
}
