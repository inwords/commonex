package com.inwords.expenses.feature.events.ui.choose_person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.appbar.TopAppBarWithNavIconAndText
import com.inwords.expenses.core.ui.design.loading.DefaultProgressIndicator
import com.inwords.expenses.core.ui.design.preview.ScreenPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.feature.events.ui.choose_person.ChoosePersonPaneUiModel.PersonUiModel
import expenses.shared.feature.events.generated.resources.Res
import expenses.shared.feature.events.generated.resources.common_back
import expenses.shared.feature.events.generated.resources.events_choose_person_current
import expenses.shared.feature.events.generated.resources.events_choose_person_guidance
import expenses.shared.feature.events.generated.resources.events_choose_person_heading
import expenses.shared.feature.events.generated.resources.events_choose_person_title
import expenses.shared.feature.events.generated.resources.events_empty_state
import expenses.shared.feature.events.generated.resources.events_error_state
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ChoosePersonPane(
    modifier: Modifier = Modifier,
    state: SimpleScreenState<ChoosePersonPaneUiModel>,
    onPersonSelected: (Long) -> Unit,
    onNavIconClicked: () -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBarWithNavIconAndText(
                onNavIconClicked = onNavIconClicked,
                title = stringResource(Res.string.events_choose_person_title),
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(Res.string.common_back),
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().consumeWindowInsets(paddingValues).padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is SimpleScreenState.Success -> ChoosePersonContent(
                    state = state.data,
                    onPersonSelected = onPersonSelected,
                )

                is SimpleScreenState.Loading -> DefaultProgressIndicator()
                is SimpleScreenState.Empty -> Text(
                    text = stringResource(Res.string.events_empty_state),
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )

                is SimpleScreenState.Error -> Text(
                    text = stringResource(Res.string.events_error_state),
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
internal fun ChoosePersonContent(
    modifier: Modifier = Modifier,
    state: ChoosePersonPaneUiModel,
    onPersonSelected: (Long) -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxSize()
            .selectableGroup(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item(key = "heading", contentType = "heading") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = state.eventName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.events_choose_person_heading),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(Res.string.events_choose_person_guidance),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
        itemsIndexed(items = state.persons, key = { _, person -> person.id }, contentType = { _, _ -> "person" }) { index, person ->
            PersonSelectionItem(
                person = person,
                onPersonSelected = onPersonSelected,
                modifier = Modifier.clip(
                    MaterialTheme.shapes.extraLarge.copy(
                        topStart = if (index == 0) MaterialTheme.shapes.extraLarge.topStart else CornerSize(0.dp),
                        topEnd = if (index == 0) MaterialTheme.shapes.extraLarge.topEnd else CornerSize(0.dp),
                        bottomStart = if (index == state.persons.lastIndex) MaterialTheme.shapes.extraLarge.bottomStart else CornerSize(0.dp),
                        bottomEnd = if (index == state.persons.lastIndex) MaterialTheme.shapes.extraLarge.bottomEnd else CornerSize(0.dp),
                    )
                ),
            )
        }
    }
}

@Composable
private fun PersonSelectionItem(
    person: PersonUiModel,
    onPersonSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth()
            .background(if (person.selected) colorScheme.primaryContainer else colorScheme.surfaceContainerLow)
            .heightIn(min = 80.dp)
            .selectable(selected = person.selected, role = Role.RadioButton) { onPersonSelected(person.id) }
            .testTag("events_choose_person_${person.id}")
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(colorScheme.surfaceContainer, CircleShape)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = person.initial,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = person.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (person.selected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
            )
            if (person.selected) {
                Text(
                    text = stringResource(Res.string.events_choose_person_current),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onPrimaryContainer,
                )
            }
        }
        RadioButton(
            selected = person.selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colorScheme.onPrimaryContainer),
        )
    }
}

@ScreenPreviews
@Preview(name = "Expanded", widthDp = 840, heightDp = 600)
@Composable
private fun ChoosePersonPanePreview() {
    CommonExTheme {
        ChoosePersonPane(
            state = SimpleScreenState.Success(
                ChoosePersonPaneUiModel(
                    eventId = 1,
                    eventName = "Weekend in Lisbon",
                    persons = persistentListOf(
                        PersonUiModel(id = 1, name = "Sam", selected = true),
                        PersonUiModel(id = 2, name = "Alex", selected = false),
                        PersonUiModel(id = 3, name = "Jo", selected = false),
                    )
                )
            ),
            onPersonSelected = {},
            onNavIconClicked = {},
        )
    }
}

@Preview(name = "Loading", widthDp = 360, heightDp = 800)
@Composable
private fun ChoosePersonPaneLoadingPreview() {
    ChoosePersonPaneStateSample(SimpleScreenState.Loading)
}

@Preview(name = "Error", widthDp = 360, heightDp = 800)
@Composable
private fun ChoosePersonPaneErrorPreview() {
    ChoosePersonPaneStateSample(SimpleScreenState.Error)
}

@Preview(name = "Empty", widthDp = 360, heightDp = 800)
@Composable
private fun ChoosePersonPaneEmptyPreview() {
    ChoosePersonPaneStateSample(SimpleScreenState.Empty)
}

@Composable
private fun ChoosePersonPaneStateSample(state: SimpleScreenState<ChoosePersonPaneUiModel>) {
    CommonExTheme {
        ChoosePersonPane(state = state, onPersonSelected = {}, onNavIconClicked = {})
    }
}
