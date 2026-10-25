package com.inwords.expenses.feature.banner.data.network

import com.inwords.expenses.core.network.HostConfig
import com.inwords.expenses.core.network.requestWithExceptionHandling
import com.inwords.expenses.core.network.toIoResult
import com.inwords.expenses.core.network.url
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.core.utils.SuspendLazy
import com.inwords.expenses.feature.banner.domain.Banner
import com.inwords.expenses.feature.banner.domain.BannerRepository
import com.inwords.expenses.feature.banner.domain.BannerResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException

internal class BannerRemoteStoreImpl(
    private val client: SuspendLazy<HttpClient>,
    private val hostConfig: HostConfig,
) : BannerRepository {

    override suspend fun getBanner(): IoResult<BannerResult> {
        return client.requestWithExceptionHandling {
            val response = get {
                url(hostConfig) {
                    pathSegments = listOf("api", "user", "banner")
                }
            }
            when (response.status) {
                HttpStatusCode.OK -> {
                    val dto = response.body<BannerResponseDto>()
                    BannerResult(
                        banner = if (dto.text.isBlank()) {
                            null
                        } else {
                            Banner(type = dto.type, text = dto.text)
                        }
                    )
                }

                HttpStatusCode.NoContent -> BannerResult(null)

                else -> throw ContentConvertException("Unexpected banner response status: ${response.status.value}")
            }
        }.toIoResult()
    }
}
