package com.inwords.expenses.core.ui.design.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.preview.ScreenPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import expenses.shared.core.ui_design.generated.resources.Res
import expenses.shared.core.ui_design.generated.resources.common_close
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SingleChoiceBottomSheet(
    title: String,
    options: List<SingleChoiceOption<T>>,
    onOptionClicked: (T) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = if (LocalInspectionMode.current) SheetValue.Expanded else SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    fun hideSheet() {
        scope.launch {
            sheetState.hide()
            if (!sheetState.isVisible) {
                onDismissRequest()
            }
        }
    }

    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = ::hideSheet) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(Res.string.common_close),
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .selectableGroup(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(options, key = { it.key }, contentType = { "option" }) { option ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (option.selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (option.selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .then(if (option.testTag == null) Modifier else Modifier.testTag(option.testTag))
                            .selectable(
                                selected = option.selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onOptionClicked(option.payload)
                                    hideSheet()
                                },
                            )
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option.text,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = if (option.supportingText == null) Modifier.weight(1f) else Modifier.widthIn(min = 48.dp),
                        )
                        if (option.supportingText != null) {
                            Text(
                                text = option.supportingText,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (option.selected) {
                            Icon(imageVector = Icons.Outlined.Check, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@ScreenPreviews
@Preview(name = "Expanded", widthDp = 840, heightDp = 600)
@Composable
private fun SingleChoiceBottomSheetPreview() {
    CommonExTheme {
        SingleChoiceBottomSheet(
            title = "Group currency",
            options = listOf(
                SingleChoiceOption(
                    key = "EUR",
                    text = "EUR",
                    supportingText = "Euro",
                    selected = true,
                    payload = "EUR",
                ),
                SingleChoiceOption(
                    key = "USD",
                    text = "USD",
                    supportingText = "US Dollar",
                    selected = false,
                    payload = "USD",
                ),
                SingleChoiceOption(
                    key = "RUB",
                    text = "RUB",
                    supportingText = "Russian Ruble",
                    selected = false,
                    payload = "RUB",
                ),
            ),
            onOptionClicked = {},
            onDismissRequest = {},
        )
    }
}

@Preview(name = "Names without supporting text", widthDp = 360, heightDp = 800)
@Composable
private fun SingleChoiceBottomSheetNamesPreview() {
    CommonExTheme {
        SingleChoiceBottomSheet(
            title = "Paid by",
            options = listOf(
                SingleChoiceOption(key = "alex", text = "Alex", supportingText = null, selected = true, payload = "alex"),
                SingleChoiceOption(key = "sam", text = "Sam", supportingText = null, selected = false, payload = "sam"),
                SingleChoiceOption(key = "jo", text = "Jo", supportingText = null, selected = false, payload = "jo"),
            ),
            onOptionClicked = {},
            onDismissRequest = {},
        )
    }
}
