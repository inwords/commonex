# Mobile Sync And Sharing

This document is the canonical mobile reference for the KMM offline-first model, event join/share links, and sync task behavior.

## Source Of Truth

- Shared event domain and join logic: `android/shared/feature/events/src/commonMain/kotlin/com/inwords/expenses/feature/events/domain/`
- Shared expense sync logic: `android/shared/feature/expenses/src/commonMain/kotlin/com/inwords/expenses/feature/expenses/domain/tasks/`
- Shared remote stores: `android/shared/feature/events/src/commonMain/kotlin/com/inwords/expenses/feature/events/data/network/store/`
  and `android/shared/feature/expenses/src/commonMain/kotlin/com/inwords/expenses/feature/expenses/data/network/`
- Mobile menu/share UI logic: `android/shared/feature/menu/src/commonMain/kotlin/com/inwords/expenses/feature/menu/ui/MenuViewModel.kt`
- Deep-link plumbing: `android/shared/core/navigation/src/**/DeeplinkProvider*.kt`,
  `android/shared/core/navigation/src/commonMain/kotlin/com/inwords/expenses/core/navigation/DeeplinkHandler.kt`,
  `android/app/src/main/AndroidManifest.xml`, `android/iosApp/iosApp/iOSApp.swift`

## Identity Model

- Events, persons, currencies, and expenses use a local numeric ID plus an optional `serverId`.
- `serverId == null` means the record exists only locally and still needs sync.
- Joining an already-known remote event is idempotent at the mobile domain layer:
  `JoinEventUseCase` first looks up the event by `serverId` locally and, if found, only switches the current event.

## Current Event Selection

- The shared mobile layer stores the current event ID in `SettingsRepository`.
- `JoinEventUseCase.joinLocalEvent(...)` and successful remote joins both set the current event immediately.
- Leaving or deleting an event is handled through event-domain use cases; sync tasks assume the current event already exists locally.

## Join Flows

- The join destination is `/event/{eventId}` with optional `pinCode` or `token` query parameters.
- `JoinEventPaneDestination` and `JoinEventViewModel` require exactly one credential:
  access code or token.
- If currencies are missing locally or still have `serverId == null`, mobile pulls currencies before joining a remote event.
- Token joins map backend outcomes into user-facing states:
  invalid token, expired token, event not found, or generic failure.
- Backend `410 Gone` for an event join triggers local cleanup by `serverId` and is surfaced as not found.

## Deep Links

- Android registers verified app links for `http(s)://commonex.ru/event/*` in `AndroidManifest.xml`.
- iOS declares `applinks:commonex.ru` in `iosApp.entitlements` and forwards both `onOpenURL` and browser user activity into the shared KMM navigation layer.
- `HandleDeeplinks` matches incoming URLs against Navigation deep-link definitions and navigates to the matching destination.
- `JoinEventViewModel` auto-triggers join when the incoming deep link already contains a token or pin code.

### iOS Universal Links — AASA file missing (as of 2026-03-21)

iOS Universal Links require the server to host an Apple App Site Association file at `https://commonex.ru/.well-known/apple-app-site-association`. Currently the server returns an HTML page (Next.js fallback) instead of the required JSON. Without a valid AASA file, iOS will never open Universal Links in the app — they will always open in Safari.

**To fix**, the server must serve the following at `/.well-known/apple-app-site-association` with `Content-Type: application/json`:

```json
{
  "applinks": {
    "details": [
      {
        "appIDs": ["<TEAM_ID>.<BUNDLE_ID>"],
        "components": [
          { "/": "/event/*" }
        ]
      }
    ]
  }
}
```

Replace `<TEAM_ID>` and `<BUNDLE_ID>` with the actual Apple Developer Team ID and the app's bundle identifier. This is a server/Next.js configuration fix — the iOS app code (`onContinueUserActivity` handler) is already correctly implemented.

## Share Links

- Mobile share text is created from the current event state in `MenuViewModel`.
- Preferred share flow:
  request a backend share token and generate `https://commonex.ru/event/{eventId}?token={token}`.
- Fallback share flow:
  if share-token creation fails, generate `https://commonex.ru/event/{eventId}?pinCode={pinCode}`.
- The fallback is intentionally less secure and is used only when the token request fails.

## Event And Participant Sync

- `EventPersonsPushTask` only runs for events that already have a `serverId`.
- It pushes persons whose `serverId` is still null.
- On success it writes back remote person IDs through the local store in a transaction.

## Expense Sync

- `EventExpensesPushTask` requires all three prerequisites before it can push:
  synced event, synced persons, and synced currencies.
- It pushes only local expenses whose `serverId` is null.
- Partial network responses (success for some expenses, error for others) are supported: iterate over the full results list with index so alignment with local expenses is preserved; persist only successful results.
- After a successful push it writes back the remote expense `serverId` and the backend-confirmed `exchangedAmount` values for each split.
- Mobile sends `amount` for every split and includes client-supplied `exchangedAmount` only when the local expense uses a custom rate (`isCustomRate == true`).

- `EventExpensesPullTask` has the same synced-event/person/currency prerequisites.
- Pull currently performs insert-only reconciliation:
  it fetches remote expenses and upserts only those whose remote `serverId` does not already exist locally.
- Pulled V2 expense payloads must include `isCustomRate`; mobile preserves that backend state so custom-rate expenses created on another client keep their custom conversion semantics after sync.
- Current implication:
  pull does not update or delete previously synced local expenses when the server changes an existing remote expense.

## Currency Handling

- Mobile pulls currencies and rates from `/api/v3/user/currencies/all` through the shared event remote store.
- `CurrenciesPullTask` refreshes currencies during sync and reconciles local rows by currency code while preserving local IDs.
- Currency rates are persisted in Room on the `currency` table as unscaled integer plus exponent/scale values and reconstructed with `BigDecimal.fromBigIntegerWithExponent(...)`.
- Frequent exchange operations read from a Room-backed in-memory cache populated from `CurrenciesLocalStore`; the cache snapshots both the currency lookup and USD-rate lookup atomically while Room remains the source of truth.
- Shared mobile persists currency metadata in Room with the last seen currency-list `ETag` and a required local rates-update date. The date is initialized for both fresh installs and `2->3` upgrades, so callers do not need to handle nullable date state.
- New installs and migrated installs use the same seeded offline-capable rate snapshot, so currency conversion still works on first launch without network access.
- Backend-created expenses remain the authoritative source for synced `exchangedAmount` values.
