package com.inwords.expenses.core.ui.design.legal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val uriHandler = LocalUriHandler.current
        Text(
            modifier = Modifier.clickable {
                uriHandler.openUriSafe(privacyPolicyUrl())
                onPrivacyPolicyClicked()
            }.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically),
            text = stringResource(Res.string.privacy_policy),
            style = MaterialTheme.typography.bodySmall,
        )

        Text(
            modifier = Modifier.align(Alignment.CenterVertically),
            text = "·",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            modifier = Modifier.clickable {
                uriHandler.openUriSafe(termsOfUseUrl())
                onTermsOfUseClicked()
            }.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically),
            text = stringResource(Res.string.terms_of_use),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Preview
@Composable
private fun LegalBlockPreview() {
    CommonExTheme {
        LegalBlock(
            onPrivacyPolicyClicked = {},
            onTermsOfUseClicked = {}
        )
    }
}
