package com.inwords.expenses.core.ui.design.legal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.preview.ComponentPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.core.ui.utils.openUriSafe
import com.inwords.expenses.core.ui.utils.privacyPolicyUrl
import com.inwords.expenses.core.ui.utils.termsOfUseUrl
import expenses.shared.core.ui_design.generated.resources.Res
import expenses.shared.core.ui_design.generated.resources.privacy_policy
import expenses.shared.core.ui_design.generated.resources.terms_of_use
import org.jetbrains.compose.resources.stringResource

@Composable
fun LegalBlock(
    onPrivacyPolicyClicked: () -> Unit,
    onTermsOfUseClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        // Leave room for native touch-target expansion when the links wrap.
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        val uriHandler = LocalUriHandler.current
        Text(
            modifier = Modifier.clickable {
                uriHandler.openUriSafe(privacyPolicyUrl())
                onPrivacyPolicyClicked()
            }.widthIn(min = 48.dp),
            text = stringResource(Res.string.privacy_policy),
            style = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "·",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                modifier = Modifier.clickable {
                    uriHandler.openUriSafe(termsOfUseUrl())
                    onTermsOfUseClicked()
                }.widthIn(min = 48.dp),
                text = stringResource(Res.string.terms_of_use),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@ComponentPreviews
@Preview(name = "Russian / large text", widthDp = 320, fontScale = 2f, locale = "ru")
@Composable
private fun LegalBlockPreview() {
    CommonExTheme {
        Surface {
            LegalBlock(
                modifier = Modifier.padding(16.dp),
                onPrivacyPolicyClicked = {},
                onTermsOfUseClicked = {},
            )
        }
    }
}
