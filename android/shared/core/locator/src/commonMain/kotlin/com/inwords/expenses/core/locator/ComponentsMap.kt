package com.inwords.expenses.core.locator

import com.inwords.expenses.core.utils.Component
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass

object ComponentsMap : SynchronizedObject() {
    private val components = mutableMapOf<KClass<out Component>, Lazy<Component>>()

    fun <T : Component> registerComponent(kClass: KClass<T>, component: Lazy<T>) {
        synchronized(this) {
            components[kClass] = component
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Component> getComponent(kClass: KClass<T>): T {
        synchronized(this) {
            return components[kClass]?.value as T
        }
    }

}

inline fun <reified T : Component> ComponentsMap.registerComponent(component: Lazy<T>) {
    registerComponent(T::class, component)
}

inline fun <reified T : Component> ComponentsMap.getComponent(): T {
    return getComponent(T::class)
}

inline fun <reified T : Component> ComponentsMap.inject(): ReadOnlyProperty<Any?, T> {
    return ReadOnlyProperty { _, _ -> getComponent<T>() }
}



