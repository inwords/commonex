package com.inwords.expenses.feature.events.ui.create

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.appbar.TopAppBarWithNavIconAndText
import com.inwords.expenses.core.ui.design.button.ButtonWithIconAndText
import com.inwords.expenses.core.ui.design.selection.SingleChoiceBottomSheet
import com.inwords.expenses.core.ui.design.selection.SingleChoiceOption
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.feature.events.ui.common.PersonNameField
import com.inwords.expenses.feature.events.ui.create.CreateEventPaneUiModel.CurrencyInfoUiModel
import expenses.shared.feature.events.generated.resources.Res
import expenses.shared.feature.events.generated.resources.common_back
import expenses.shared.feature.events.generated.resources.events_create_event_button
import expenses.shared.feature.events.generated.resources.events_create_title
import expenses.shared.feature.events.generated.resources.events_name_placeholder
import expenses.shared.feature.events.generated.resources.events_person_name_placeholder
import expenses.shared.feature.events.generated.resources.events_setup_add_participants
import expenses.shared.feature.events.generated.resources.events_setup_add_person
import expenses.shared.feature.events.generated.resources.events_setup_currency_label
import expenses.shared.feature.events.generated.resources.events_setup_currency_picker_title
import expenses.shared.feature.events.generated.resources.events_setup_event_example
import expenses.shared.feature.events.generated.resources.events_setup_heading
import expenses.shared.feature.events.generated.resources.events_your_name_label
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CreateEventPane(
    state: CreateEventPaneUiModel,
    onEventNameChanged: (String) -> Unit,
    onOwnerNameChanged: (String) -> Unit,
    onParticipantNameChanged: (Int, String) -> Unit,
    onAddParticipantClicked: () -> Unit,
    onCurrencyClicked: (CurrencyInfoUiModel) -> Unit,
    onConfirmClicked: () -> Unit,
    onNavIconClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBarWithNavIconAndText(
                onNavIconClicked = onNavIconClicked,
                title = stringResource(Res.string.events_create_title),
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(Res.string.common_back),
            )
        },
    ) { paddingValues ->
        val focusRequester = remember { FocusRequester() }
        val listState = rememberLazyListState()
        var requestFocus by remember { mutableStateOf(false) }
        LaunchedEffect(state.persons.size) {
            if (requestFocus) {
                listState.scrollToItem(4 + state.persons.lastIndex)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(paddingValues)
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .testTag("events_create_form"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "heading", contentType = "heading") {
                    Text(
                        text = stringResource(Res.string.events_setup_heading),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).semantics { heading() },
                    )
                }
                item(key = "event", contentType = "field") {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("events_create_name"),
                        value = state.eventName,
                        onValueChange = onEventNameChanged,
                        label = { Text(stringResource(Res.string.events_name_placeholder)) },
                        placeholder = { Text(stringResource(Res.string.events_setup_event_example)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        ),
                    )
                }
                item(key = "owner", contentType = "field") {
                    PersonNameField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("events_owner_name"),
                        participantName = state.ownerName,
                        labelText = stringResource(Res.string.events_your_name_label),
                        enabled = true,
                        imeAction = ImeAction.Done,
                        onImeAction = onConfirmClicked,
                        onParticipantNameChanged = onOwnerNameChanged,
                    )
                }
                item(key = "currency", contentType = "field") {
                    SetupCurrencyField(
                        currencies = state.currencies,
                        selectedCurrencyCode = state.selectedCurrencyCode,
                        enabled = state.isCurrencySelectionEnabled,
                        onCurrencyClicked = onCurrencyClicked,
                    )
                }
                itemsIndexed(
                    items = state.persons,
                    key = { index, _ -> "participant_$index" },
                    contentType = { _, _ -> "field" },
                ) { index, name ->
                    PersonNameField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("events_participant_$index")
                            .then(
                                if (index == state.persons.lastIndex) {
                                    Modifier
                                        .focusRequester(focusRequester)
                                        .onGloballyPositioned {
                                            if (requestFocus) {
                                                focusRequester.requestFocus()
                                                requestFocus = false
                                            }
                                        }
                                } else {
                                    Modifier
                                }
                            ),
                        participantName = name,
                        labelText = stringResource(Res.string.events_person_name_placeholder),
                        enabled = true,
                        imeAction = ImeAction.Done,
                        onImeAction = onConfirmClicked,
                        onParticipantNameChanged = { onParticipantNameChanged(index, it) },
                    )
                }
                item(key = "add_person", contentType = "guidance") {
                    TextButton(
                        modifier = Modifier.testTag("events_add_participant"),
                        onClick = {
                            onAddParticipantClicked()
                            requestFocus = true
                        },
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                        Text(
                            text = stringResource(
                                if (state.persons.isEmpty()) {
                                    Res.string.events_setup_add_participants
                                } else {
                                    Res.string.events_setup_add_person
                                }
                            ),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                item(key = "confirm", contentType = "action") {
                    ButtonWithIconAndText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("events_create_confirm"),
                        onClick = onConfirmClicked,
                        enabled = state.isConfirmEnabled,
                        text = stringResource(Res.string.events_create_event_button),
                        imageVector = Icons.Outlined.Check,
                        minHeight = ButtonDefaults.MediumContainerHeight,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupCurrencyField(
    currencies: List<CurrencyInfoUiModel>,
    selectedCurrencyCode: String?,
    enabled: Boolean,
    onCurrencyClicked: (CurrencyInfoUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isCurrencyPickerVisible by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("events_create_currency"),
        onClick = { isCurrencyPickerVisible = true },
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.events_setup_currency_label),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (selectedCurrencyCode != null) {
                Text(
                    text = selectedCurrencyCode,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
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
    if (isCurrencyPickerVisible) {
        SingleChoiceBottomSheet(
            modifier = Modifier.testTag("events_currency_picker"),
            title = stringResource(Res.string.events_setup_currency_picker_title),
            options = remember(currencies) {
                currencies.map { currency ->
                    SingleChoiceOption(
                        key = currency.currencyCode,
                        text = currency.currencyCode,
                        supportingText = currency.currencyName,
                        selected = currency.selected,
                        payload = currency,
                        testTag = "events_currency_${currency.currencyCode}",
                    )
                }
            },
            onOptionClicked = onCurrencyClicked,
            onDismissRequest = { isCurrencyPickerVisible = false },
        )
    }
}

@Preview
@Composable
private fun CreateEventPanePreview() {
    CommonExTheme {
        CreateEventPane(
            state = CreateEventPaneUiModel(
                eventName = "",
                ownerName = "",
                persons = persistentListOf(),
                currencies = persistentListOf(
                    CurrencyInfoUiModel(
                        currencyName = "US Dollar",
                        currencyCode = "USD",
                        selected = false
                    ),
                    CurrencyInfoUiModel(
                        currencyName = "Euro",
                        currencyCode = "EUR",
                        selected = true
                    ),
                    CurrencyInfoUiModel(
                        currencyName = "Russian Ruble",
                        currencyCode = "RUB",
                        selected = false
                    ),
                    CurrencyInfoUiModel(
                        currencyName = "Turkish Lira",
                        currencyCode = "TRY",
                        selected = false
                    ),
                    CurrencyInfoUiModel(
                        currencyName = "Japanese Yen",
                        currencyCode = "JPY",
                        selected = false
                    ),
                    CurrencyInfoUiModel(
                        currencyName = "UAE Dirham",
                        currencyCode = "AED",
                        selected = false
                    )
                ),
                selectedCurrencyCode = "EUR",
                isCurrencySelectionEnabled = true,
            ),
            onEventNameChanged = {},
            onOwnerNameChanged = {},
            onParticipantNameChanged = { _, _ -> },
            onAddParticipantClicked = {},
            onCurrencyClicked = {},
            onConfirmClicked = {},
            onNavIconClicked = {},
        )
    }
}
