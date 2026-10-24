package com.inwords.expenses.core.network

import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.serialization.ContentConvertException
import kotlinx.io.IOException

sealed interface NetworkResult<out T : Any> {

    data class Ok<out T : Any>(val data: T) : NetworkResult<T>

    sealed interface Error : NetworkResult<Nothing> {

        sealed interface Http : Error {
            data class Client(val exception: ClientRequestException) : Http
            data class Server(val exception: ServerResponseException) : Http
            data class Redirect(val exception: RedirectResponseException) : Http
        }

        data class Transport(val exception: IOException) : Error

        data class Parse(val exception: ContentConvertException) : Error

        data class NoTransformation(val exception: NoTransformationFoundException) : Error

    }

}
