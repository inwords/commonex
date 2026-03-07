package com.inwords.expenses.feature.events.ui.join

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.inwords.expenses.core.navigation.Destination
import com.inwords.expenses.core.navigation.NavModule
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.navigation.deeplinkHostPath
import com.inwords.expenses.core.navigation.navDeepLink
import com.inwords.expenses.feature.events.api.EventsComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JoinEventPaneDestination(
    @SerialName("eventId")
    val eventId: String, // no default value so that is added as a **path parameter** to deep link
    @SerialName("pinCode")
    val pinCode: String = "",
    @SerialName("token")
    val token: String = "",
) : Destination {

    constructor() : this(eventId = "")
}

internal fun EventsComponent.getJoinEventPaneNavModule(
    navigationController: NavigationController,
): NavModule {
    val serializer = JoinEventPaneDestination.serializer()
    return NavModule(
        actualSerializer = serializer,
        deepLinks = listOf(navDeepLink(basePath = "$deeplinkHostPath/event", route = serializer)),
    ) {
        entry<JoinEventPaneDestination> { key ->
            val viewModel = viewModel<JoinEventViewModel>(factory = viewModelFactory {
                initializer {
                    JoinEventViewModel(
                        navigationController = navigationController,
                        joinEventUseCase = joinEventUseCaseLazy.value,
                        initialEventId = key.eventId,
                        initialPinCode = key.pinCode,
                        initialToken = key.token,
                    )
                }
            })
            JoinEventPane(
                state = viewModel.state.collectAsStateWithLifecycle().value,
                onEventIdChanged = viewModel::onEventIdChanged,
                onEventAccessCodeChanged = viewModel::onEventAccessCodeChanged,
                onConfirmClicked = viewModel::onConfirmClicked,
                onNavIconClicked = viewModel::onNavIconClicked,
            )
        }
    }
}
