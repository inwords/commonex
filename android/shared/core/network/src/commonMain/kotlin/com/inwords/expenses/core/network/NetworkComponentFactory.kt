package com.inwords.expenses.core.network

expect class NetworkComponentFactory(deps: NetworkComponentFactory.Deps) {

    interface Deps

    fun create(): NetworkComponent
}
