package com.inwords.expenses.core.storage.utils

import androidx.annotation.GuardedBy
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import okio.Path
import kotlin.concurrent.Volatile

class DataStoreSingleton<T>(
    private val serializer: OkioSerializer<T>,
    private val corruptionHandler: ReplaceFileCorruptionHandler<T>? = null,
    private val produceMigrations: () -> List<DataMigration<T>> = { emptyList() },
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {

    private val lock = SynchronizedObject()

    @GuardedBy("lock")
    @Volatile
    private var instance: DataStore<T>? = null

    fun getOrCreate(producePath: () -> Path): DataStore<T> {
        return instance ?: synchronized(lock) {
            if (instance == null) {
                instance = DataStoreFactory.create(
                    storage = OkioStorage(fileSystemSystem, serializer, producePath = producePath),
                    corruptionHandler = corruptionHandler,
                    migrations = produceMigrations(),
                    scope = scope
                )
            }
            instance!!
        }
    }
}
