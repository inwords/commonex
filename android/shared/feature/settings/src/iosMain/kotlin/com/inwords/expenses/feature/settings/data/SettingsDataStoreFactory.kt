package com.inwords.expenses.feature.settings.data

import androidx.datastore.core.DataStore
import com.inwords.expenses.core.storage.utils.iosDocumentsDirectoryPath

internal actual class SettingsDataStoreFactory {

    actual fun createSettingsDataStore(): DataStore<Settings> {
        return settingsDataStore.getOrCreate {
            iosDocumentsDirectoryPath() / settingsDsFileName
        }
    }

}
