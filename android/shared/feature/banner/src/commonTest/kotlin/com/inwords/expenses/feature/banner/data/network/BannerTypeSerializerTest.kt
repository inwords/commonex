package com.inwords.expenses.feature.banner.data.network

import com.inwords.expenses.feature.banner.domain.BannerType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

internal class BannerTypeSerializerTest {

    @Test
    fun `serializes banner enum values using the backend wire names`() {
        for ((type, wireType) in listOf(
            BannerType.GeneralWarning to "general_warning",
            BannerType.UpdateRequired to "update_required",
        )) {
            val dto = BannerResponseDto(type = type, text = "Message")
            assertEquals("""{"type":"$wireType","text":"Message"}""", Json.encodeToString(dto))
        }
    }
}
