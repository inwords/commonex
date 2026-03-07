package com.inwords.expenses.feature.share.api

expect class ShareComponentFactory(deps: Deps) {

    interface Deps

    fun create(): ShareComponent
}
