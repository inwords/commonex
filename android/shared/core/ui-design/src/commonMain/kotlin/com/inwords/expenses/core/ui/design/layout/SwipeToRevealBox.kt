package com.inwords.expenses.core.ui.design.layout

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import com.inwords.expenses.core.ui.design.preview.ComponentPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme

enum class RevealState { Hidden, Revealed }

private enum class SwipeToRevealSlot { Actions, Content }

@Composable
fun SwipeToRevealBox(
    actions: @Composable RowScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    state: AnchoredDraggableState<RevealState> = rememberSwipeToRevealBoxState(),
) {
    SubcomposeLayout(
        modifier = modifier.clipToBounds()
    ) { constraints ->
        // Measure content
        val contentPlaceables = subcompose(SwipeToRevealSlot.Content) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(state.requireOffset().fastRoundToInt(), 0) }
                    .anchoredDraggable(
                        state = state,
                        orientation = Orientation.Horizontal,
                    ),
                content = content
            )
        }.map { it.measure(constraints) }

        val layoutHeight = contentPlaceables.maxOfOrNull { it.height } ?: 0
        val layoutWidth = constraints.maxWidth

        // Measure actions with the content height as fixed constraint
        // When actions are hidden (offset is 0 or NaN), clear their semantics
        // so they won't be found by accessibility services or UI tests
        val actionsPlaceables = subcompose(SwipeToRevealSlot.Actions) {
            val offset = state.offset
            val isActionsVisible = !offset.isNaN() && offset <= -1f

            Row(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .then(if (isActionsVisible) Modifier else Modifier.clearAndSetSemantics {}),
                content = actions
            )
        }.map { it.measure(constraints.copy(minWidth = 0, maxHeight = layoutHeight)) }

        val actionsWidth = actionsPlaceables.maxOfOrNull { it.width } ?: 0

        // Update anchors after content composition
        state.updateAnchors(
            DraggableAnchors {
                RevealState.Hidden at 0f
                if (actionsWidth > 0) {
                    RevealState.Revealed at -actionsWidth.toFloat()
                }
            }
        )

        layout(layoutWidth, layoutHeight) {
            // Place actions aligned to the end
            actionsPlaceables.forEach { placeable ->
                placeable.placeRelative(
                    x = layoutWidth - actionsWidth,
                    y = 0
                )
            }

            // Place content on top
            contentPlaceables.forEach { placeable ->
                placeable.placeRelative(x = 0, y = 0)
            }
        }
    }
}

@Composable
fun rememberSwipeToRevealBoxState(initialValue: RevealState = RevealState.Hidden): AnchoredDraggableState<RevealState> {
    return rememberSaveable(
        saver = AnchoredDraggableState.Saver()
    ) {
        AnchoredDraggableState(initialValue = initialValue)
    }
}

@ComponentPreviews
@Composable
private fun SwipeToRevealBoxHiddenPreview() {
    SwipeToRevealBoxPreviewContent(initialValue = RevealState.Hidden)
}

@Preview(name = "Revealed", widthDp = 360)
@Composable
private fun SwipeToRevealBoxRevealedPreview() {
    SwipeToRevealBoxPreviewContent(initialValue = RevealState.Revealed)
}

@Composable
private fun SwipeToRevealBoxPreviewContent(initialValue: RevealState) {
    CommonExTheme {
        Surface {
            SwipeToRevealBox(
                modifier = Modifier.padding(16.dp),
                state = rememberSwipeToRevealBoxState(initialValue = initialValue),
                actions = {
                    FilledTonalButton(onClick = {}) { Text("Remove") }
                },
                content = {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Text(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            text = "Weekend in Lisbon",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
            )
        }
    }
}
