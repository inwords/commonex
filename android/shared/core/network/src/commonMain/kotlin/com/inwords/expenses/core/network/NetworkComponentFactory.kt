package com.inwords.expenses.core.network

expect class NetworkComponentFactory(deps: Deps) {

    interface Deps : NetworkComponentFactoryCommonDeps

    fun create(): NetworkComponent
}

interface NetworkComponentFactoryCommonDeps {

    val versionCode: Int
}
