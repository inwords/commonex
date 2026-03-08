package com.inwords.expenses.core.network

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies User-Agent format per docs/network-contracts.md:
 * CommonEx/<versionCode> (<platform>/<buildType>)
 * - platform: Android | iOS
 * - buildType: r (release) | d (debug)
 */
internal class UserAgentTest {

    @Test
    fun `buildUserAgent format for Android release`() {
        assertEquals("CommonEx/123 (Android/r)", buildUserAgent(123, "Android", production = true))
    }

    @Test
    fun `buildUserAgent format for Android debug`() {
        assertEquals("CommonEx/123 (Android/d)", buildUserAgent(123, "Android", production = false))
    }

    @Test
    fun `buildUserAgent format for iOS release`() {
        assertEquals("CommonEx/456 (iOS/r)", buildUserAgent(456, "iOS", production = true))
    }

    @Test
    fun `buildUserAgent format for iOS debug`() {
        assertEquals("CommonEx/456 (iOS/d)", buildUserAgent(456, "iOS", production = false))
    }

    @Test
    fun `buildUserAgent with versionCode zero`() {
        assertEquals("CommonEx/0 (iOS/d)", buildUserAgent(0, "iOS", production = false))
    }
}
