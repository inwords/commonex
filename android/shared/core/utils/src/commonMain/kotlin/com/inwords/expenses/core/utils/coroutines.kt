package com.inwords.expenses.core.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.experimental.ExperimentalTypeInference
import kotlin.time.Duration

val UI = Dispatchers.Main.immediate
val DF = Dispatchers.Default
val IO = Dispatchers.IO
val UNCONFINED = Dispatchers.Unconfined

fun <T> Flow<T>.collectIn(
    scope: CoroutineScope,
    collector: FlowCollector<T>
) {
    val flow = this
    scope.launch {
        flow.collect(collector)
    }
}

fun <T> Flow<T>.collectLatestIn(
    scope: CoroutineScope,
    action: suspend (value: T) -> Unit
) {
    val flow = this
    scope.launch {
        flow.collectLatest(action)
    }
}

fun <T> Flow<T>.stateInWhileSubscribed(
    scope: CoroutineScope,
    initialValue: T,
    stopTimeoutMillis: Long = 1500L,
    replayExpirationMillis: Long = Long.MAX_VALUE
): StateFlow<T> {
    return stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(
            stopTimeoutMillis = stopTimeoutMillis,
            replayExpirationMillis = replayExpirationMillis
        ),
        initialValue = initialValue,
    )
}

fun <T> Flow<T>.shareInWhileSubscribed(
    scope: CoroutineScope,
    replay: Int = 0,
    stopTimeoutMillis: Long = 1500L,
    replayExpirationMillis: Long = 1500L,
): Flow<T> {
    return this.shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(
            stopTimeoutMillis = stopTimeoutMillis,
            replayExpirationMillis = replayExpirationMillis
        ),
        replay = replay,
    )
}

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTypeInference::class)
inline fun <T, R> Flow<T>.flatMapLatestNoBuffer(
    @BuilderInference crossinline transform: suspend (value: T) -> Flow<R>
): Flow<R> {
    return this.flatMapLatest(transform)
        .buffer(0)
}

@OptIn(FlowPreview::class)
fun <T> Flow<T>.debounceAfterInitial(
    timeout: Duration,
): Flow<T> {
    var initial = true
    return this.debounce {
        if (initial) {
            initial = false
            Duration.ZERO
        } else {
            timeout
        }
    }
}

@Suppress("UNCHECKED_CAST")
inline fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    flow9: Flow<T9>,
    flow10: Flow<T10>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) -> R
): Flow<R> = combine(flow, flow2, flow3, flow4, flow5, flow6, flow7, flow8, flow9, flow10) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
        args[6] as T7,
        args[7] as T8,
        args[8] as T9,
        args[9] as T10,
    )
}
