package ru.commonex

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.appfunctions.service.AppFunctionConfiguration
import androidx.work.Configuration
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.integration.base.appfunctions.createAppFunctionConfiguration
import com.inwords.expenses.integration.base.enableSync
import com.inwords.expenses.integration.base.initializeSentry
import com.inwords.expenses.integration.base.registerComponents
import kotlinx.coroutines.asExecutor

class App : Application(), Configuration.Provider, AppFunctionConfiguration.Provider {

    override val appFunctionConfiguration: AppFunctionConfiguration
        get() = createAppFunctionConfiguration()

    private val production = !BuildConfig.DEBUG && BuildConfig.BUILD_TYPE != "autotest"

    override fun onCreate() {
        super.onCreate()

        initializeSentry(production = production)

        if (!production) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyFlashScreen()
                    .build()
            )

            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }

        registerComponents(this, production = production)

        enableSync()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerCoroutineContext(IO)
            .setTaskExecutor(IO.limitedParallelism(4).asExecutor())
            .setMinimumLoggingLevel(
                if (production) {
                    Log.ASSERT
                } else {
                    Log.INFO
                }
            )
            .build()
}