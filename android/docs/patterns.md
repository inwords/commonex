# Android Coding Patterns

## Coding Patterns

- **Default arguments:** Generally discouraged until they are truly conceptually optional.
- **Dependency Injection:** Uses custom locator pattern in `shared:core:locator`
- **Navigation:** Compose Navigation with deep linking support
- **State Management:** ViewModels with StateFlow for UI state
- **Database:** Room with KMP support, entities in feature modules
- **Network:** Ktor client with Cronet backend for Android
- **Async Operations:** Coroutines with structured concurrency

### Integration Layer and Domain Delegation

Integration entry points (AppFunctions, deep links, etc.) should delegate to domain interactors instead of duplicating code. Parse and validate input, resolve entities by name/ID, then call the existing domain method. Avoid reimplementing business logic, calculations, or persistence in the integration layer.

## ViewModel Patterns

### Job Guard Pattern

Prevent duplicate concurrent operations using nullable `Job?` with null check:

```kotlin
private var shareJob: Job? = null

fun onShareClicked() {
    if (shareJob != null) return  // Guard against concurrent execution

    shareJob = viewModelScope.launch {
        // ... async work
    }.apply {
        invokeOnCompletion { shareJob = null }  // Clear on completion (success or failure)
    }
}
```

Use `invokeOnCompletion` for cleanup instead of try-finally to handle both success and cancellation.

### StateFlow.update for Atomic Updates

Use `update {}` instead of direct assignment for thread-safe state transitions:

```kotlin
state.update { currentState ->
    currentState.copy(joining = EventJoiningState.Error(errorMessage))
}
```

Use `updateAndGet` when you need both the operation and the updated value:

```kotlin
val state = state.updateAndGet { it.copy(joining = EventJoiningState.Joining) }
```

### Property with Backing Field

Expose immutable `StateFlow` while using mutable `MutableStateFlow` internally:

```kotlin
val state: StateFlow<UiModel>
field = MutableStateFlow(initialState)
```

### State Change Detection in Combine

Track previous values to detect changes and trigger side effects:

```kotlin
val state = run {
    var lastServerId: String? = null

    combine(
        eventFlow.onEach { event ->
            val serverId = event?.serverId
            if (lastServerId != serverId) {
                lastServerId = serverId
                shareState.value = ShareState.Idle(serverId)  // Reset on change
            }
        },
        shareState
    ) { event, shareState -> ... }
}
```

### Expense Revert Confirmation via Navigation3 Dialog

For expense details (`ExpenseItemPane`), keep revert confirmation in a dedicated Navigation3 dialog destination, not in local remembered composable state.

Required flow:

1. Expenses list opens `ExpenseItemPaneDestination` with both `expenseId` and `eventId`.
2. `ExpenseItemPaneViewModel` handles revert click by navigating to `ExpenseRevertDialogDestination(expenseId, eventId, expenseDescription)`.
3. `ExpenseRevertDialogViewModel` confirm action:
    - guards duplicate taps with `Job?`
    - loads event by `eventId` and expense by `expenseId` from stores
    - executes `revertExpense(...)`
    - pops back to `ExpensesPaneDestination` (`inclusive = false`)
4. If event/expense is missing, close dialog safely (`popBackStack()`).

Do not introduce a shared state holder for this confirmation flow.

## Compose UI Patterns

### State-Driven Side Effects (Clipboard, Share)

Trigger platform operations via state changes rather than direct callbacks:

```kotlin
// ViewModel: Set state to trigger UI-side operation
shareState.value = ShareState.PendingClipboardCopy(shareText)

// Composable: React to state and perform operation
(state.shareState as? ShareState.PendingClipboardCopy)?.let { pendingCopy ->
    LaunchedEffect(pendingCopy.shareText) {
        clipboard.setClipEntry(clipEntryOf(state.eventName, pendingCopy.shareText))
        onTextCopied()  // Callback to transition state
    }
}

// ViewModel: Transition to next state after operation
fun onTextCopied() {
    shareState.update { if (it is PendingClipboardCopy) Ready(it.shareText) else it }
}
```

### Accessibility-Safe Loading Swap

When swapping between loading indicator and content, and it might cause UI jumping, preserve layout size and hide from screen readers:

```kotlin
Box(contentAlignment = Alignment.Center) {
    // Always render content to preserve intrinsic size for any font scale
    Text(
        modifier = Modifier
            .alpha(if (isLoading) 0f else 1f)
            .then(if (isLoading) Modifier.clearAndSetSemantics {} else Modifier)
            .clickable(enabled = !isLoading, onClick = onClick),
        text = stringResource(Res.string.action)
    )
    if (isLoading) {
        LoadingIndicator(modifier = Modifier.size(24.dp))
    }
}
```

- `alpha(0f)` hides visually but preserves layout
- `clearAndSetSemantics {}` removes from accessibility tree when invisible
- Never use fixed `dp` sizes for dynamic text content (breaks with font scale settings)

### Derived Values Without Remember

Simple derived values from state parameters don't need `remember`:

```kotlin
val isLoading = state.shareState is ShareState.Loading  // Correct: recalculates on recomposition
```

Use `remember` only for expensive computations or mutable state that must survive recomposition.

### Overlay-Safe UI Test Selectors

For bottom sheets and dialogs, avoid global text-based waits/assertions because the same text can exist on the underlying screen.

- Add stable overlay-specific `testTag`s for root and key values/actions.
- In screen objects, assert using `onNodeWithTag(...)`/`onAllNodesWithTag(...)` against those tags.
- For dynamic rows (for example split persons), add deterministic per-item tags (prefix + stable value) and assert by that tag.

### Conditional Modifier Composition

Prefer `Modifier.then(...)` for conditional modifier chaining over wrapping the base modifier in `.let { ... }`.

```kotlin
modifier = Modifier
    .weight(0.56f)
    .then(if (valueTestTag == null) Modifier else Modifier.testTag(valueTestTag))
```

## UI State Modeling

### Sealed Interface with Computed Properties

Define polymorphic behavior via extension properties in companion:

```kotlin
sealed interface ShareState {
    data class Idle(val serverId: String?, val pinCode: String) : ShareState
    data object Loading : ShareState
    data class Ready(val shareText: String) : ShareState
    data class PendingClipboardCopy(val shareText: String) : ShareState

    companion object {
        val ShareState.canShare: Boolean // Use computed property if it's clearly derived from state
            get() = when (this) {
                is Idle -> serverId != null
                Loading -> false
                is PendingClipboardCopy, is Ready -> true
            }
    }
}
```

### States Carrying Context Data

Different states carry different data relevant to that state:

- `Idle(serverId, pinCode)` - data needed to initiate action
- `Ready(shareText)` - result of completed action
- `PendingClipboardCopy(shareText)` - transient state triggering UI operation

## Combining Data States with UI States

Complex screens often need to combine data from repositories/use cases with transient UI states (refresh indicators, recently deleted items, etc.). Use layered combination to avoid recalculating expensive transformations.

### Layered State Combination Pattern

Separate data flows from UI state flows and combine them at different layers:

```kotlin
// Layer 1: UI state flows (cheap, frequently changing)
private val recentlyRemovedEventName = MutableStateFlow<String?>(null)
private val isRefreshingFlow = currentEventFlow
    .flatMapLatestNoBuffer { event ->
        if (event == null) flowOf(false)
        else pullToRefreshStateManager.isEventRefreshing(event.id)
    }
    .shareInWhileSubscribed(scope = viewModelScope, replay = 1)

// Layer 2: Data flows combined with some UI state
private val localEventsState = flow {
    combine(
        getEventsUseCase.getEvents(),           // Data
        eventDeletionStateManager.state,        // Data
        recentlyRemovedEventName,               // UI state (cheap)
    ) { events, deletionState, removedName ->
        // Transform to UI model
    }.let { emitAll(it) }
}

// Layer 3: Final state - combine expensive data flow with cheap UI state last
val state: StateFlow<ScreenState> = combine(
    expensesDetailsFlow,
    settingsRepository.getCurrentPersonId(),
) { details, personId ->
    // Expensive transformation here
    details to personId
}.flatMapLatestNoBuffer { (details, personId) ->
    // Build UI model from data
    flowOf(buildUiModel(details, personId))
}
    .combine(isRefreshingFlow) { state, isRefreshing ->
        // Late combination: only update isRefreshing field
        if (state is Success && state.data.isRefreshing != isRefreshing) {
            state.copy(data = state.data.copy(isRefreshing = isRefreshing))
        } else {
            state
        }
    }
    .stateInWhileSubscribed(scope = viewModelScope, initialValue = Loading)
```

Key principles:

- **Separate cheap UI state from expensive data** - `isRefreshingFlow` changes frequently but shouldn't trigger data recalculation
- **Combine UI state last** - After expensive transformations, so changes to UI state only update the final field
- **Conditional updates** - Check if value actually changed before copying to avoid unnecessary emissions
- **Use shareInWhileSubscribed for shared flows** - When the same flow is used in multiple combines

### Transient UI State with Auto-Clear

For temporary UI states like "recently deleted" notifications:

```kotlin
private val recentlyRemovedEventName = MutableStateFlow<String?>(null)
private var recentlyRemovedEventJob: Job? = null

private fun handleEventRemoval(removedEvent: Event) {
    recentlyRemovedEventName.value = null
    recentlyRemovedEventJob?.cancel()

    recentlyRemovedEventJob = viewModelScope.launch {
        recentlyRemovedEventName.value = removedEvent.name
        delay(3000) // Show for 3 seconds
        recentlyRemovedEventName.value = null
    }
}
```

## Form Input Patterns

### Multiple Input Flows Combined

For forms with many inputs, use separate `MutableStateFlow` for each input and combine them:

```kotlin
private val selectedCurrencyCode = MutableStateFlow<String?>(null)
private val selectedPersonId = MutableStateFlow<Long?>(null)
private val inputDescription = MutableStateFlow("")
private val inputAmount = MutableStateFlow(AmountModel(null, ""))
private val inputEqualSplit = MutableStateFlow(true)

private val _state = combine(
    dataFlow,
    selectedCurrencyCode,
    selectedPersonId,
    inputDescription,
    inputAmount,
    inputEqualSplit,
) { data, currency, person, description, amount, equalSplit ->
    // Build screen model from all inputs
}
```

### Input with Fallback to Settings

Use `flatMapLatestNoBuffer` to provide default from settings when user hasn't selected:

```kotlin
private val selectedPersonId = MutableStateFlow<Long?>(null)

// In combine:
selectedPersonId.flatMapLatestNoBuffer { selected ->
    selected?.let { flowOf(it) } ?: settingsRepository.getCurrentPersonId()
}
```

### Lazy Set Initialization

Initialize sets/collections from current state only on first interaction:

```kotlin
private val selectedIds = MutableStateFlow<Set<Long>?>(null) // null = all selected

fun onItemClicked(id: Long) {
    selectedIds.update { current ->
        val ids = if (current == null) {
            // Initialize from state on first click
            (_state.value as? Success)?.data?.items
                ?.mapTo(HashSet()) { it.id }
                ?: return@update current
        } else {
            current
        }

        if (ids.contains(id)) ids - id else ids + id
    }
}
```

### Raw + Parsed Input Model

Store both raw string and parsed value for inputs requiring validation:

```kotlin
data class AmountModel(
    val amount: BigDecimal?,  // Parsed value for calculations (null if invalid)
    val amountRaw: String,    // Raw string to preserve user input in UI
)

fun onAmountChanged(input: String) {
    val trimmed = input.trim()
    inputAmount.value = AmountModel(
        amount = trimmed.toBigDecimalOrNull(),
        amountRaw = trimmed
    )
}
```

This allows validation (`amount != null`) while preserving exactly what user typed.

### Private Domain Model + Public UI Model

Separate internal domain model from UI model for complex screens:

```kotlin
// Internal model with domain objects
private val _state: StateFlow<SimpleScreenState<AddExpenseScreenModel>> = combine(...) { ... }

// Public UI model for composables (no domain objects)
val state: StateFlow<SimpleScreenState<AddExpensePaneUiModel>> = _state
    .map { state ->
        when (state) {
            is SimpleScreenState.Success -> SimpleScreenState.Success(state.data.toUiModel())
            SimpleScreenState.Loading -> SimpleScreenState.Loading
            SimpleScreenState.Error -> SimpleScreenState.Error
            SimpleScreenState.Empty -> SimpleScreenState.Empty
        }
    }
    .stateInWhileSubscribed(viewModelScope, initialValue = SimpleScreenState.Loading)
```

Benefits:

- Internal model can reference domain entities (Person, Currency)
- UI model uses only primitive types and IDs
- `toUiModel()` transformation is explicit and testable

### Pre-filled Forms from Navigation

Initialize input flows from navigation arguments:

```kotlin
class AddExpenseViewModel(
    private val replenishment: Replenishment?,  // Navigation argument
) {
    private val selectedExpenseType = MutableStateFlow(
        replenishment?.let { ExpenseType.Replenishment } ?: ExpenseType.Spending
    )
    private val inputAmount = MutableStateFlow(
        if (replenishment == null) {
            AmountModel(null, "")
        } else {
            AmountModel(replenishment.amount.toBigDecimalOrNull(), replenishment.amount)
        }
    )
}
```

## Numeric BigDecimal Patterns

### Exchange Rate Formatting Without `scale()`

When formatting computed exchange rates, avoid `BigDecimal.scale(...)` based formatters (for example `toRoundedString`) because IonSpin can throw:

`ArithmeticException: Negative decimal precision is not allowed`

Use API-driven division and rounding, then fixed-scale formatting:

```kotlin
private const val EXCHANGE_RATE_SCALE = 2L

val roundedRate = totalExchangedAmount.abs()
    .divide(
        other = totalOriginalAmount.abs(),
        decimalMode = DecimalMode(
            decimalPrecision = estimateDivisionPrecision(/*...*/),
            roundingMode = RoundingMode.ROUND_HALF_TO_EVEN,
        ),
    )
    .roundToDigitPositionAfterDecimalPoint(
        digitPosition = EXCHANGE_RATE_SCALE,
        roundingMode = RoundingMode.ROUND_HALF_TO_EVEN,
    )
```

Then render exactly 2 decimals using arithmetic (for example multiply by `BigDecimal.TEN.pow(2)`, convert to integer digits, split whole/fraction parts), not by applying a second decimal rounding step.

### API Output Formatting

For API or AppFunction amount output (e.g. debt amounts, expense mutation results), use `toPlainDecimalString()` from `shared/core/utils` instead of `BigDecimal.toString()`. IonSpin's `toString()` can produce scientific notation (e.g. `"1.0E+1"`); `toPlainDecimalString()` yields plain decimals (e.g. `"10"`).

### Required Edge Tests For Exchange Rate

For exchange-rate formatting logic, keep tests for these cases:

- very small ratio (must render `0.00`)
- very large ratio (must render full value with `.00`)
- zero original total (must return hidden/null state)
