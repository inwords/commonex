package com.inwords.expenses.feature.expenses.ui.add

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.preview.ComponentPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneUiModel.ExchangeRateUiModel
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_add_converted_total
import expenses.shared.feature.expenses.generated.resources.expenses_add_exchange_rate_invalid
import expenses.shared.feature.expenses.generated.resources.expenses_exchange_rate
import expenses.shared.feature.expenses.generated.resources.expenses_exchange_rate_value
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExpenseExchangeRateInput(
    exchangeRate: ExchangeRateUiModel,
    enabled: Boolean,
    onExchangeRateChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            val keyboardController = LocalSoftwareKeyboardController.current
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_expense_exchange_rate_input"),
                value = exchangeRate.rateRaw,
                onValueChange = onExchangeRateChanged,
                enabled = enabled,
                label = { Text(stringResource(Res.string.expenses_exchange_rate)) },
                isError = !exchangeRate.isValid,
                supportingText = {
                    Text(
                        text = if (exchangeRate.isValid) {
                            stringResource(
                                Res.string.expenses_exchange_rate_value,
                                exchangeRate.originalCurrencyCode,
                                exchangeRate.rateRaw,
                                exchangeRate.primaryCurrencyCode,
                            )
                        } else {
                            stringResource(Res.string.expenses_add_exchange_rate_invalid)
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                singleLine = true,
            )
            val motionScheme = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = exchangeRate.convertedTotalAmount,
                modifier = Modifier.fillMaxWidth(),
                contentKey = { it != null },
                transitionSpec = {
                    fadeIn(motionScheme.fastEffectsSpec())
                        .togetherWith(fadeOut(motionScheme.fastEffectsSpec()))
                        .using(SizeTransform { _, _ -> motionScheme.defaultSpatialSpec() })
                },
            ) { amount ->
                if (amount != null) {
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = stringResource(
                            Res.string.expenses_add_converted_total,
                            amount,
                            exchangeRate.primaryCurrencyCode,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@ComponentPreviews
@Composable
private fun ExpenseExchangeRateInputPreview() {
    CommonExTheme {
        ExpenseExchangeRateInput(
            exchangeRate = ExchangeRateUiModel(
                originalCurrencyCode = "USD",
                primaryCurrencyCode = "EUR",
                rateRaw = "0.92",
                isCustom = true,
                isValid = true,
                convertedTotalAmount = "55.20",
            ),
            enabled = true,
            onExchangeRateChanged = {},
        )
    }
}

@Preview(name = "Invalid custom rate", widthDp = 360)
@Composable
private fun ExpenseExchangeRateInputInvalidPreview() {
    CommonExTheme {
        ExpenseExchangeRateInput(
            exchangeRate = ExchangeRateUiModel(
                originalCurrencyCode = "USD",
                primaryCurrencyCode = "EUR",
                rateRaw = "0",
                isCustom = true,
                isValid = false,
                convertedTotalAmount = null,
            ),
            enabled = true,
            onExchangeRateChanged = {},
        )
    }
}
