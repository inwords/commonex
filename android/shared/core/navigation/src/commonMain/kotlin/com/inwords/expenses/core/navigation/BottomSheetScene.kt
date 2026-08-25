package com.inwords.expenses.core.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.inwords.expenses.core.navigation.BottomSheetSceneStrategy.Companion.bottomSheet

/**
 * An [OverlayScene] that renders an [entry] within a [androidx.compose.material3.ModalBottomSheet].
 * https://github.com/android/nav3-recipes/blob/main/app/src/main/java/com/example/nav3recipes/bottomsheet/BottomSheetSceneStrategy.kt
 * */
@OptIn(ExperimentalMaterial3Api::class)
internal class BottomSheetScene<T : Any>(
    override val key: T,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val modalBottomSheetProperties: ModalBottomSheetProperties,
    private val onBack: () -> Unit,
) : OverlayScene<T> {

    private var onDismissedCalled = false

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable (() -> Unit) = {
        ModalBottomSheet(
            onDismissRequest = {
                if (!onDismissedCalled) {
                    onDismissedCalled = true
                    onBack()
                }
            },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
            properties = modalBottomSheetProperties,
        ) {
            entry.Content()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as BottomSheetScene<*>

        return key == other.key &&
            previousEntries == other.previousEntries &&
            overlaidEntries == other.overlaidEntries &&
            entry == other.entry &&
            modalBottomSheetProperties == other.modalBottomSheetProperties
    }

    override fun hashCode(): Int {
        return key.hashCode() * 31 +
            previousEntries.hashCode() * 31 +
            overlaidEntries.hashCode() * 31 +
            entry.hashCode() * 31 +
            modalBottomSheetProperties.hashCode() * 31
    }

    override fun toString(): String {
        return "BottomSheetScene(key=$key, entry=$entry, previousEntries=$previousEntries, overlaidEntries=$overlaidEntries, modalBottomSheetProperties=$modalBottomSheetProperties)"
    }
}

/**
 * A [SceneStrategy] that displays entries that have added [bottomSheet] to their [NavEntry.metadata]
 * within a [androidx.compose.material3.ModalBottomSheet] instance.
 *
 * This strategy should always be added before any non-overlay scene strategies.
 */
@OptIn(ExperimentalMaterial3Api::class)
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull()
        val bottomSheetProperties = lastEntry?.metadata?.get(BOTTOM_SHEET_KEY) as? ModalBottomSheetProperties
        val previousEntries = if (entries.size > 1) {
            entries.subList(0, entries.size - 1)
        } else {
            entries
        }
        return bottomSheetProperties?.let { properties ->
            @Suppress("UNCHECKED_CAST")
            BottomSheetScene(
                key = lastEntry.contentKey as T,
                previousEntries = previousEntries,
                overlaidEntries = previousEntries,
                entry = lastEntry,
                modalBottomSheetProperties = properties,
                onBack = onBack
            )
        }
    }

    companion object {
        /**
         * Function to be called on the [NavEntry.metadata] to mark this entry as something that
         * should be displayed within a [androidx.compose.material3.ModalBottomSheet].
         *
         * @param modalBottomSheetProperties properties that should be passed to the containing
         * [androidx.compose.material3.ModalBottomSheet].
         */
        @OptIn(ExperimentalMaterial3Api::class)
        fun bottomSheet(
            modalBottomSheetProperties: ModalBottomSheetProperties = ModalBottomSheetProperties()
        ): Map<String, Any> = mapOf(BOTTOM_SHEET_KEY to modalBottomSheetProperties)

        internal const val BOTTOM_SHEET_KEY = "bottomsheet"
    }
}
