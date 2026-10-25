package com.inwords.expenses.feature.banner.domain

data class Banner(
    val type: BannerType,
    val text: String,
)

internal val Banner.dismissalKey: String get() = "${type.name}:$text"
