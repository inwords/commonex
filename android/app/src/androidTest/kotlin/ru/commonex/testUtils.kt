package ru.commonex

import androidx.compose.ui.test.junit4.ComposeTestRule
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest as runCoroutineTest

/**
 * Runs a test block within the context of the [ComposeTestRule].
 *
 * This utility simplifies test code by:
 * 1. Wrapping the block in [runCoroutineTest] for coroutine support
 * 2. Providing the [ComposeTestRule] as a context receiver to the block
 *
 * Compose rule waits use wall-clock time, and the application's main dispatcher stays unchanged.
 * The timeout matches the five-minute batch limit in Marathonfile for complete UI flows.
 *
 * Usage:
 * ```kotlin
 * private val composeRule = createAndroidComposeRule<MainActivity>()
 *
 * @Test
 * fun testSomeFlow() = composeRule.runTest {
 *     LocalEventsScreen()
 *         .clickCreateEvent()
 *         .enterEventName("Test")
 * }
 * ```
 */
internal inline fun ComposeTestRule.runTest(crossinline block: suspend context(ComposeTestRule) () -> Unit) {
    runCoroutineTest(timeout = 5.minutes) {
        block(this@runTest)
    }
}
