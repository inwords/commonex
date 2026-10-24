package ru.commonex

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Annotation to mark tests that should run in offline mode.
 * When applied, network connectivity is disabled before the test and restored after.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class Offline

/**
 * JUnit 4 Rule that manages network connectivity for tests.
 * Tests annotated with [Offline] will have network disabled during execution.
 */
internal class ConnectivityRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement {
        val isOffline = description.getAnnotation(Offline::class.java) != null

        return object : Statement() {
            override fun evaluate() {
                try {
                    if (isOffline) {
                        ConnectivityManager.turnOffDataAndWifi()
                    }
                    base.evaluate()
                } finally {
                    if (isOffline) {
                        ConnectivityManager.turnOnDataAndWifi()
                    }
                }
            }
        }
    }
}
