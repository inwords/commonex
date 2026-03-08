package com.inwords.expenses.feature.events.api

actual class EventsComponentFactory(private val deps: Deps) {
    actual interface Deps : EventsComponentFactoryCommonDeps

    actual fun create(): EventsComponent {
        return EventsComponent(deps = deps)
    }
}
