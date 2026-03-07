package com.inwords.expenses.feature.settings.api

import com.inwords.expenses.feature.settings.data.SettingsDataStoreFactory

actual class SettingsComponentFactory actual constructor(
    private val deps: Deps
) {

    actual interface Deps

    actual fun create(): SettingsComponent {
        return SettingsComponent(SettingsDataStoreFactory())
    }
}
