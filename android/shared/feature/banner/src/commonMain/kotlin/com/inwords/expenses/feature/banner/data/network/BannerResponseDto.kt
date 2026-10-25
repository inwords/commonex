package com.inwords.expenses.feature.banner.data.network

import com.inwords.expenses.feature.banner.domain.BannerType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class BannerResponseDto(

    @SerialName("type")
    @Serializable(with = BannerTypeSerializer::class)
    val type: BannerType,

    @SerialName("text")
    val text: String,
)
