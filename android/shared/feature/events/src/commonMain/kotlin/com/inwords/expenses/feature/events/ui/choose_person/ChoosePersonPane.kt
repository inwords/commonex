package com.inwords.expenses.feature.events.ui.choose_person

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateInt
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.appbar.TopAppBarWithNavIconAndText
import com.inwords.expenses.core.ui.design.loading.DefaultProgressIndicator
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.feature.events.ui.choose_person.ChoosePersonPaneUiModel.PersonUiModel
import com.inwords.expenses.feature.events.ui.common.EventInfoBlock
import expenses.shared.feature.events.generated.resources.Res
import expenses.shared.feature.events.generated.resources.common_back
import expenses.shared.feature.events.generated.resources.events_choose_person_title
import expenses.shared.feature.events.generated.resources.events_empty_state
import expenses.shared.feature.events.generated.resources.events_error_state
import expenses.shared.feature.events.generated.resources.events_your_name_label
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ChoosePersonPane(
    modifier: Modifier = Modifier,
    state: SimpleScreenState<ChoosePersonPaneUiModel>,
    onPersonSelected: (Long) -> Unit,
    onNavIconClicked: () -> Unit,
) {
    when (state) {
        is SimpleScreenState.Success -> ChoosePersonContent(
            modifier = modifier,
            state = state.data,
            onPersonSelected = onPersonSelected,
            onNavIconClicked = onNavIconClicked,
        )

        is SimpleScreenState.Loading -> ChoosePersonPaneLoading(modifier = modifier)

        is SimpleScreenState.Empty -> Box(modifier = modifier.fillMaxSize()) {
            Text(
                text = stringResource(Res.string.events_empty_state),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        is SimpleScreenState.Error -> Box(modifier = modifier.fillMaxSize()) {
            Text(
                text = stringResource(Res.string.events_error_state),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChoosePersonContent(
    modifier: Modifier = Modifier,
    state: ChoosePersonPaneUiModel,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .consumeWindowInsets(paddingValues)
                .padding(horizontal = 8.dp)
                .padding(paddingValues)
        ) {
            EventInfoBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                eventName = state.eventName,
                currentPersonName = null
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(Res.string.events_your_name_label),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 4.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.persons.forEach { person ->
                    PersonSelectionItem(
                        person = person,
                        onPersonSelected = onPersonSelected,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChoosePersonPaneLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DefaultProgressIndicator()
    }
}

@Composable
private fun PersonSelectionItem(
    person: PersonUiModel,
    onPersonSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val transition = updateTransition(person.selected, label = "transition_enabled")
    val animatedBorderColor by transition.animateColor(
        label = "border_color",
        transitionSpec = { tween(durationMillis = 300) }
    ) { selected -> if (selected) colorScheme.primary else colorScheme.outline }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onPersonSelected(person.id) }
            .border(
                width = 1.dp,
                color = animatedBorderColor,
                shape = MaterialTheme.shapes.small
            )
            .padding(all = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val animatedBackgroundColor by transition.animateColor(
            label = "background_color",
            transitionSpec = { tween(durationMillis = 300) }
        ) { selected -> if (selected) colorScheme.primary else colorScheme.surfaceVariant }
        val animatedTextColor by transition.animateColor(
            label = "text_color",
            transitionSpec = { tween(durationMillis = 300) }
        ) { selected -> if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant }

        Text(
            modifier = Modifier
                .background(
                    color = animatedBackgroundColor,
                    shape = CircleShape
                )
                .size(32.dp)
                .wrapContentHeight(Alignment.CenterVertically),
            text = person.name.firstOrNull()?.toString() ?: "",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = animatedTextColor
        )

        Spacer(modifier = Modifier.width(12.dp))

        val animatedFontWeight by transition.animateInt(
            label = "font_weight",
            transitionSpec = { tween(durationMillis = 300) }
        ) { selected -> if (selected) FontWeight.SemiBold.weight else FontWeight.Normal.weight }

        Text(
            text = person.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight(animatedFontWeight),
            color = colorScheme.onSurface
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
