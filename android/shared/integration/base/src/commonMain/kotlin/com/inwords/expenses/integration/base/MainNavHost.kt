package com.inwords.expenses.integration.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.inwords.expenses.core.locator.ComponentsMap
import com.inwords.expenses.core.locator.getComponent
import com.inwords.expenses.core.navigation.BottomSheetSceneStrategy
import com.inwords.expenses.core.navigation.DeeplinkProvider
import com.inwords.expenses.core.navigation.Destination
import com.inwords.expenses.core.navigation.HandleDeeplinks
import com.inwords.expenses.core.navigation.rememberNavigationController
import com.inwords.expenses.feature.events.api.EventsComponent
import com.inwords.expenses.feature.expenses.api.ExpensesComponent
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneDestination
import com.inwords.expenses.feature.menu.api.MenuComponent
import kotlinx.serialization.modules.SerializersModule

@Composable
fun MainNavHost(
    deeplinkProvider: DeeplinkProvider,
    modifier: Modifier = Modifier,
    startDestination: Destination = ExpensesPaneDestination
) {
    val navigationController = rememberNavigationController()

    val eventsComponent = remember { ComponentsMap.getComponent<EventsComponent>() }
    val expensesComponent = remember { ComponentsMap.getComponent<ExpensesComponent>() }
    val menuComponent = remember { ComponentsMap.getComponent<MenuComponent>() }

    val modules = remember {
        buildList {
            addAll(
                eventsComponent.getNavModules(
                    navigationController = navigationController,
                    expensesPaneDestination = ExpensesPaneDestination,
                )
            )
            addAll(
                expensesComponent.getNavModules(
                    navigationController = navigationController,
                )
            )
            addAll(
                menuComponent.getNavModules(
                    navigationController = navigationController,
                )
            )
        }
    }

    @Suppress("UNCHECKED_CAST") // TODO wait for fix in library
    val backStack = rememberNavBackStack(
        configuration = remember {
            SavedStateConfiguration {
                this.serializersModule = SerializersModule {
                    modules.forEach { include(it.serializersModule) }
                }
            }
        },
        elements = arrayOf(startDestination)
    ) as NavBackStack<Destination>
    remember(navigationController, backStack) {
        navigationController.attachTo(backStack)
    }

    HandleDeeplinks(
        deeplinkProvider = deeplinkProvider,
        navigationController = navigationController,
        navDeepLinks = remember { modules.flatMapTo(HashSet()) { it.deepLinks } }
    )

    val strategy = remember {
        BottomSheetSceneStrategy<Destination>() then
            DialogSceneStrategy() then
            SinglePaneSceneStrategy()
    }
    NavDisplay(
        modifier = modifier,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        backStack = backStack,
        onBack = backStack::removeLastOrNull,
        sceneStrategy = strategy,
        entryProvider = remember(modules) {
            entryProvider {
                modules.forEach { it.entrySupplier(this) }
            }
        },
    )
}
