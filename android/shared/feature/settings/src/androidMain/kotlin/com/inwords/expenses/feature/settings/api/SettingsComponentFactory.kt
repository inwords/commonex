package com.inwords.expenses.feature.settings.api

import android.content.Context
import com.inwords.expenses.feature.settings.data.SettingsDataStoreFactory

actual class SettingsComponentFactory actual constructor(private val deps: Deps) {

    actual interface Deps {
        val context: Context
    }

    actual fun create(): SettingsComponent {
        return SettingsComponent(SettingsDataStoreFactory(deps.context))
    }
}
