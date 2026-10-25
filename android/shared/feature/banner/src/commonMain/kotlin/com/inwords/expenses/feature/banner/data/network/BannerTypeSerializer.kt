package com.inwords.expenses.feature.banner.data.network

import com.inwords.expenses.feature.banner.domain.BannerType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

internal object BannerTypeSerializer : KSerializer<BannerType> {

    override val descriptor = PrimitiveSerialDescriptor("BannerType", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BannerType = when (val value = decoder.decodeString()) {
        "general_warning" -> BannerType.GeneralWarning
        "update_required" -> BannerType.UpdateRequired
        else -> throw SerializationException("Unknown banner type: $value")
    }

    override fun serialize(encoder: Encoder, value: BannerType) {
        encoder.encodeString(
            when (value) {
                BannerType.GeneralWarning -> "general_warning"
                BannerType.UpdateRequired -> "update_required"
            }
        )
    }
}
