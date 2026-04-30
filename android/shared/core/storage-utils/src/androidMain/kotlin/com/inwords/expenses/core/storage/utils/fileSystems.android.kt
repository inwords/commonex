package com.inwords.expenses.core.storage.utils

import okio.FileSystem

actual val fileSystemSystem: FileSystem
    get() = FileSystem.SYSTEM
