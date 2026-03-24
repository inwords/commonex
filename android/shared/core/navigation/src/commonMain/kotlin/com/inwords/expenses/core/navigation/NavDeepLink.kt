package com.inwords.expenses.core.navigation

import com.inwords.expenses.core.observability.Observability
import com.inwords.expenses.core.observability.ObservabilityLevel
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class NavDeepLink<T : Destination>(
    private val schemes: Set<URLProtocol>,
    private val basePath: String,
    private val serializer: KSerializer<T>,
) {

    private class ParamNames(
        val pathParamNames: List<String>,
        val queryParamNames: List<String>
    )

    private val patternParamNames: ParamNames by lazy {
        val descriptor = serializer.descriptor
        val pathParams = mutableListOf<String>()
        val queryParams = mutableListOf<String>()
        for (i in 0 until descriptor.elementsCount) {
            val propertyName = descriptor.getElementName(i)
            if (descriptor.isElementOptional(i)) {
                queryParams.add(propertyName)
            } else {
                pathParams.add(propertyName)
            }
        }
        ParamNames(pathParams, queryParams)
    }

    fun getDestinationIfMatches(deeplink: String): T? {
        val deeplinkUrl = Url(deeplink)
        val baseUrl = Url(schemes.first().name + "://" + basePath)

        if (deeplinkUrl.protocol !in schemes || deeplinkUrl.host != baseUrl.host) {
            return null
        }

        val deeplinkSegments = deeplinkUrl.segments
        val baseUrlSegments = baseUrl.segments
        val baseUrlSegmentsSize = baseUrlSegments.size

        val patternPathParamNames = patternParamNames.pathParamNames
        val patternQueryParamNames = patternParamNames.queryParamNames

        if (deeplinkSegments.size != baseUrlSegmentsSize + patternPathParamNames.size) {
            return null
        }

        val deeplinkArgs = buildMap {
            for (i in patternPathParamNames.indices) {
                put(patternPathParamNames[i], deeplinkSegments[baseUrlSegmentsSize + i])
            }

            deeplinkUrl.parameters.entries().forEach { (key, values) ->
                if (patternQueryParamNames.contains(key)) {
                    put(key, values.first())
                }
            }
        }

        val jsonObject = buildJsonObject {
            deeplinkArgs.forEach { (key, value) ->
                put(key = key, value = value)
            }
        }

        return try {
            Json.decodeFromJsonElement(serializer, jsonObject)
        } catch (exception: IllegalArgumentException) {
            Observability.captureException(exception) {
                level = ObservabilityLevel.WARNING
                setMessage("NavDeepLink failed to decode a matched deeplink")
                setContext("base_path", basePath)
                setContext("deeplink_host", deeplinkUrl.host)
                setContext("deeplink_path", deeplinkSegments.joinToString(separator = "/", prefix = "/"))
                setContext("query_keys", deeplinkUrl.parameters.entries().joinToString(",") { (key, _) -> key })
            }
            null
        }
    }
}

fun <T : Destination> navDeepLink(
    schemes: Set<URLProtocol> = setOf(URLProtocol.HTTPS, URLProtocol.HTTP),
    basePath: String,
    route: KSerializer<T>,
): NavDeepLink<T> {
    return NavDeepLink(schemes, basePath, route)
}
