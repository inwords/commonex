package com.inwords.expenses.core.navigation

import io.ktor.http.URLProtocol
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class NavDeepLinkTest {

    private val deepLink = navDeepLink(basePath = "commonex.test/event", route = EventDestination.serializer())

    @Test
    fun `matching routes decode path and query values`() {
        assertEquals(
            EventDestination(eventId = "trip 1", token = "access+token"),
            deepLink.getDestinationIfMatches("https://commonex.test/event/trip%201?token=access%2Btoken&unrelated=ignored"),
        )
        assertEquals(
            EventDestination(eventId = "trip-2"),
            deepLink.getDestinationIfMatches("http://commonex.test/event/trip-2"),
        )
    }

    @Test
    fun `routes with missing or extra path arguments do not match`() {
        listOf(
            "https://commonex.test/event",
            "https://commonex.test/event/trip-1/extra",
        ).forEach { url ->
            assertNull(deepLink.getDestinationIfMatches(url), url)
        }
    }

    @Test
    fun `arguments incompatible with the destination type do not match`() {
        val countLink = navDeepLink(basePath = "commonex.test/count", route = CountDestination.serializer())

        assertEquals(CountDestination(7), countLink.getDestinationIfMatches("https://commonex.test/count/7"))
        assertNull(countLink.getDestinationIfMatches("https://commonex.test/count/not-a-number"))
    }

    @Test
    fun `foreign hosts and disallowed schemes do not match`() {
        val httpsOnly = navDeepLink(
            schemes = setOf(URLProtocol.HTTPS),
            basePath = "commonex.test/event",
            route = EventDestination.serializer(),
        )

        listOf(
            "https://other.test/event/trip-1",
            "https://commonex.test.other.test/event/trip-1",
            "http://commonex.test/event/trip-1",
            "ftp://commonex.test/event/trip-1",
        ).forEach { url ->
            assertNull(httpsOnly.getDestinationIfMatches(url), url)
        }
    }

    @Serializable
    private data class EventDestination(val eventId: String, val token: String? = null) : Destination

    @Serializable
    private data class CountDestination(val count: Int) : Destination
}
