package com.inwords.expenses.feature.banner.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.inwords.expenses.core.ui.utils.openUriSafe
import com.inwords.expenses.feature.banner.domain.Banner

@Composable
fun BannerContent(
    banner: Banner?,
    onDismissClick: () -> Unit,
    appStoreUrl: String,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = banner,
        contentKey = { it != null },
        transitionSpec = {
            fadeIn(motionScheme.fastEffectsSpec())
                .togetherWith(fadeOut(motionScheme.fastEffectsSpec()))
                .using(SizeTransform { _, _ -> motionScheme.defaultSpatialSpec() })
        },
        label = "banner",
    ) { currentBanner ->
        if (currentBanner != null) {
            BannerCard(
                banner = currentBanner,
                onDismissClick = onDismissClick,
                onUpdateClick = { uriHandler.openUriSafe(appStoreUrl) },
                modifier = modifier,
            )
        }
    }
}
