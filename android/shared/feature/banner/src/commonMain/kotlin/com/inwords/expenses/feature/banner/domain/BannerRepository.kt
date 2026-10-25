package com.inwords.expenses.feature.banner.domain

import com.inwords.expenses.core.utils.IoResult

interface BannerRepository {

    suspend fun getBanner(): IoResult<BannerResult>
}
