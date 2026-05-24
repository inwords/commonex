# iOS Native Text Input (Future)

Planned opt-in improvement for iOS text fields. **Not enabled** in the repo today.

## Context

Compose Multiplatform **1.11.0** adds **Native iOS Text Input** (NITI): `BasicTextField` can delegate editing to UIKit via `PlatformImeOptions.usingNativeTextInput(true)` in the **iOS** source set.

- [What's new in Compose Multiplatform 1.11.0](https://kotlinlang.org/docs/multiplatform/whats-new-compose-111.html#native-text-input)
- [CMP 1.11.0 release notes](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.11.0)

JetBrains positions the existing Compose-drawn input as the **stable default** for cross-platform consistency. Native input is the **iOS-tuned** path (caret, magnifier, selection, system edit menu, autocorrect, password autofill).

## Why consider it

CommonEx is form-heavy on iOS (event names, access codes, person names, expense fields). Native mode should feel **more like a standard iOS app** when typing and selecting text.

**Android is unchanged** — this flag applies only on iOS.

## Why deferred (cons)

- **`@ExperimentalComposeUiApi`** — API may still evolve.
- **Platform divergence** — iOS typing/selection behavior will differ from Android by design.
- **Newer code path** — requires device QA (caret, Done/Next, numeric fields, disabled/error states, copy/paste vs `SelectionContainer` on lists).
- **Must be wired everywhere** — only fields that pass `platformImeOptions` benefit; missed call sites stay on Compose input.
- **No global toggle** — enable via shared `KeyboardOptions` helpers, not a single app flag.

## Suggested implementation

1. Add an `expect`/`actual` helper (or `iosMain`-only extension) that returns `KeyboardOptions` with existing options plus, on iOS only:

   ```kotlin
   platformImeOptions = PlatformImeOptions { usingNativeTextInput(true) }
   ```

2. Thread through centralized field composables first (single place to opt in):
   - `shared/feature/events/.../ui/common/fields.kt` — `EventNameField`, `EventIdField`, `EventAccessCodeField`, `PersonNameField`
   - `shared/feature/expenses/.../ui/add/AddExpensePane.kt` — expense `OutlinedTextField` usages

3. Opt in at call sites with `@OptIn(ExperimentalComposeUiApi::class)` where required.

4. Run manual checks from [`ios-validation-checklist.md`](ios-validation-checklist.md) device section, focusing on all text-entry flows.

## Validation checklist (when enabled)

- [ ] Caret placement and drag; spacebar caret movement on hardware keyboard (if used)
- [ ] Double-tap word / triple-tap paragraph selection
- [ ] System context menu (Copy, Look Up, Translate where applicable)
- [ ] Autocorrect and typo replacement on name fields
- [ ] Password / numeric access code field behavior (`KeyboardType.NumberPassword`)
- [ ] `ImeAction.Done` / `ImeAction.Next` and `KeyboardActions` (`onDone`, `onAny`)
- [ ] Disabled and error states on fields
- [ ] No regression on screens with `SelectionContainer` or list copy gestures

## Related docs

- [`ios-validation-checklist.md`](ios-validation-checklist.md) — pre-submission iOS validation
- [`compose-ui-rules.md`](compose-ui-rules.md) — Compose UI standards (Android-focused; iOS text is additive)
