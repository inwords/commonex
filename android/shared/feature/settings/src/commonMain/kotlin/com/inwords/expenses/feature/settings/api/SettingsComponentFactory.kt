package com.inwords.expenses.feature.settings.api


expect class SettingsComponentFactory(deps: Deps) {

    interface Deps

    fun create(): SettingsComponent
}
