package com.inwords.expenses.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

sealed interface ConditionalGetResult<T> {
    data class Modified<T>(
        val body: T,
        val eTag: String?,
    ) : ConditionalGetResult<T>

    data class NotModified<T>(
        val eTag: String?,
    ) : ConditionalGetResult<T>
}

suspend inline fun <reified T> HttpClient.getConditional(
    eTag: String?,
    crossinline block: HttpRequestBuilder.() -> Unit,
): ConditionalGetResult<T> {
    val response = get {
        expectSuccess = false
        eTag?.let { header(HttpHeaders.IfNoneMatch, it) }
        block()
    }

    return when (response.status) {
        HttpStatusCode.OK -> ConditionalGetResult.Modified(
            body = response.body<T>(),
            eTag = response.headers[HttpHeaders.ETag],
        )

        HttpStatusCode.NotModified -> ConditionalGetResult.NotModified(
            eTag = response.headers[HttpHeaders.ETag] ?: eTag,
        )

        else -> throwConditionalGetException(response)
    }
}

@PublishedApi
internal suspend fun throwConditionalGetException(response: HttpResponse): Nothing {
    val bodyText = response.bodyAsText()
    throw when {
        response.status.value >= 500 -> ServerResponseException(response, bodyText)
        response.status.value >= 400 -> ClientRequestException(response, bodyText)
        response.status.value >= 300 -> RedirectResponseException(response, bodyText)
        else -> IllegalStateException(
            "Unexpected success/informational status in error branch: ${response.status.value}"
        )
    }
}
