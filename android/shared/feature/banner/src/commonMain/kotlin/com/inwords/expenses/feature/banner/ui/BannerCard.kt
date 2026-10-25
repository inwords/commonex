package com.inwords.expenses.feature.banner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.button.TextButtonWithText
import com.inwords.expenses.core.ui.design.icons.OpenInNew
import com.inwords.expenses.core.ui.design.layout.AdaptiveButtonsRow
import com.inwords.expenses.core.ui.design.preview.ComponentPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.feature.banner.domain.Banner
import com.inwords.expenses.feature.banner.domain.BannerType
import expenses.shared.feature.banner.generated.resources.Res
import expenses.shared.feature.banner.generated.resources.banner_general_warning_title
import expenses.shared.feature.banner.generated.resources.banner_got_it
import expenses.shared.feature.banner.generated.resources.banner_not_now
import expenses.shared.feature.banner.generated.resources.banner_update
import expenses.shared.feature.banner.generated.resources.banner_update_required_title
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BannerCard(
    banner: Banner,
    onDismissClick: () -> Unit,
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag(BannerTags.CARD),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when (banner.type) {
                        BannerType.GeneralWarning -> Icons.Outlined.Warning
                        BannerType.UpdateRequired -> Icons.Outlined.Refresh
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(
                        when (banner.type) {
                            BannerType.GeneralWarning -> Res.string.banner_general_warning_title
                            BannerType.UpdateRequired -> Res.string.banner_update_required_title
                        },
                    ),
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Text(text = banner.text, style = MaterialTheme.typography.bodyMedium)
            AdaptiveButtonsRow(horizontalSpacing = 8.dp, verticalSpacing = 8.dp) {
                TextButtonWithText(
                    onClick = onDismissClick,
                    text = stringResource(
                        when (banner.type) {
                            BannerType.GeneralWarning -> Res.string.banner_got_it
                            BannerType.UpdateRequired -> Res.string.banner_not_now
                        },
                    ),
                    modifier = Modifier.testTag(BannerTags.DISMISS_BUTTON),
                )
                if (banner.type == BannerType.UpdateRequired) {
                    val minHeight = ButtonDefaults.MediumContainerHeight
                    Button(
                        onClick = onUpdateClick,
                        modifier = Modifier.heightIn(min = minHeight).testTag(BannerTags.UPDATE_BUTTON),
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.contentPaddingFor(minHeight),
                    ) {
                        Text(
                            text = stringResource(Res.string.banner_update),
                            style = ButtonDefaults.textStyleFor(minHeight),
                        )
                        Spacer(modifier = Modifier.size(ButtonDefaults.iconSpacingFor(minHeight)))
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.iconSizeFor(minHeight)),
                        )
                    }
                }
            }
        }
    }
}

@ComponentPreviews
@Composable
private fun BannerCardUpdateRequiredPreview() {
    CommonExTheme {
        Surface {
            BannerCard(
                banner = Banner(
                    type = BannerType.UpdateRequired,
                    text = "People in your group may use new features that this version can’t display correctly",
                ),
                onDismissClick = {},
                onUpdateClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@ComponentPreviews
@Composable
private fun BannerCardGeneralWarningPreview() {
    CommonExTheme {
        Surface {
            BannerCard(
                banner = Banner(
                    type = BannerType.GeneralWarning,
                    text = "Some features are temporarily unavailable. You can continue using your saved groups.",
                ),
                onDismissClick = {},
                onUpdateClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(name = "Expanded / Russian / dark", widthDp = 840, locale = "ru")
@Composable
private fun BannerCardExpandedDarkPreview() {
    CommonExTheme(darkTheme = true) {
        Surface {
            BannerCard(
                banner = Banner(
                    type = BannerType.UpdateRequired,
                    text = "Участники группы могут использовать новые возможности, которые некорректно отображаются в этой версии",
                ),
                onDismissClick = {},
                onUpdateClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
