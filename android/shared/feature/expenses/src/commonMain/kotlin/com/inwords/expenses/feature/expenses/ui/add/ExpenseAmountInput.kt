package com.inwords.expenses.feature.expenses.ui.add

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_amount_placeholder
import expenses.shared.feature.expenses.generated.resources.expenses_amount_unavailable
import expenses.shared.feature.expenses.generated.resources.expenses_total_amount
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExpenseAmountInput(
    amount: String,
    currencyCode: String,
    readOnly: Boolean,
    isCurrencyPickerVisible: Boolean,
    enabled: Boolean,
    onAmountChanged: (String) -> Unit,
    onCurrencyClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val amountLabel = stringResource(Res.string.expenses_total_amount)
        Text(
            text = amountLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            val amountModifier = Modifier
                .weight(1f)
                .widthIn(min = 192.dp * LocalDensity.current.fontScale)
                .testTag("add_expense_amount_input")
                .semantics { contentDescription = amountLabel }
            if (readOnly) {
                Text(
                    modifier = amountModifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    text = amount.ifBlank { stringResource(Res.string.expenses_amount_unavailable) },
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                val keyboardController = LocalSoftwareKeyboardController.current
                TextField(
                    modifier = amountModifier,
                    value = amount,
                    onValueChange = onAmountChanged,
                    enabled = enabled,
                    textStyle = MaterialTheme.typography.displayMedium,
                    placeholder = {
                        Text(
                            text = stringResource(Res.string.expenses_amount_placeholder),
                            style = MaterialTheme.typography.displayMedium,
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.primary,
                        unfocusedTextColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                    singleLine = true,
                )
            }
            FilledTonalButton(
                modifier = Modifier.testTag("add_expense_currency_picker_button"),
                onClick = onCurrencyClicked,
                enabled = enabled,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text(currencyCode)
                val iconRotation by animateFloatAsState(
                    targetValue = if (isCurrencyPickerVisible) 180f else 0f,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = iconRotation },
                )
            }
        }
    }
}

@Preview(widthDp = 360)
@Composable
private fun ExpenseAmountInputEmptyPreview() {
    CommonExTheme {
        ExpenseAmountInput(
            amount = "",
            currencyCode = "EUR",
            readOnly = false,
            isCurrencyPickerVisible = false,
            enabled = true,
            onAmountChanged = {},
            onCurrencyClicked = {},
        )
    }
}

@Preview(widthDp = 360)
@Composable
private fun ExpenseAmountInputReadOnlyPreview() {
    CommonExTheme {
        ExpenseAmountInput(
            amount = "65.00",
            currencyCode = "EUR",
            readOnly = true,
            isCurrencyPickerVisible = false,
            enabled = true,
            onAmountChanged = {},
            onCurrencyClicked = {},
        )
    }
}

@Preview(widthDp = 360)
@Preview(name = "Large text", widthDp = 360, fontScale = 2f)
@Composable
private fun ExpenseAmountInputPreview() {
    CommonExTheme {
        ExpenseAmountInput(
            amount = "60.00",
            currencyCode = "EUR",
            readOnly = false,
            isCurrencyPickerVisible = false,
            enabled = true,
            onAmountChanged = {},
            onCurrencyClicked = {},
        )
    }
}
