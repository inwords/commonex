package com.inwords.expenses.feature.events.ui.choose_person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.appbar.TopAppBarWithNavIconAndText
import com.inwords.expenses.core.ui.design.loading.DefaultProgressIndicator
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.feature.events.ui.choose_person.ChoosePersonPaneUiModel.PersonUiModel
import expenses.shared.feature.events.generated.resources.Res
import expenses.shared.feature.events.generated.resources.common_back
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = stringResource(Res.string.events_choose_person_guidance),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
        }
        items(items = state.persons, key = { it.id }, contentType = { "person" }) { person ->
            PersonSelectionItem(
                person = person,
                onPersonSelected = onPersonSelected,
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
            .clip(MaterialTheme.shapes.large)
            .background(if (person.selected) colorScheme.secondaryContainer else colorScheme.surfaceContainer)
            .selectable(selected = person.selected, role = Role.RadioButton) { onPersonSelected(person.id) }
            .testTag("events_choose_person_${person.id}")
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.background(colorScheme.surfaceContainerHighest, CircleShape)
                .sizeIn(minWidth = 32.dp, minHeight = 32.dp)
                .padding(4.dp)
                .wrapContentHeight(Alignment.CenterVertically),
            text = person.name.firstOrNull()?.toString().orEmpty(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = person.name,
            style = MaterialTheme.typography.titleMedium,
            color = if (person.selected) colorScheme.onSecondaryContainer else colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = if (person.selected) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Preview
@Composable
private fun ChoosePersonPanePreview() {
    CommonExTheme {
        ChoosePersonPane(
            state = SimpleScreenState.Success(
                ChoosePersonPaneUiModel(
                    eventId = 1,
                    eventName = "Weekend Trip",
                    persons = persistentListOf(
                        PersonUiModel(id = 1, name = "John Doe", selected = true),
                        PersonUiModel(id = 2, name = "Jane Smith", selected = false),
                        PersonUiModel(id = 3, name = "Peter Jones", selected = false),
                    )
                )
            ),
            onPersonSelected = {},
            onNavIconClicked = {},
        )
    }
}
