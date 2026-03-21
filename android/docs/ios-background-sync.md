# iOS Background Sync

This document describes the approach for reliable sync on iOS.

## Current State

- iOS sync uses `GlobalScope` in `EventsSyncManager.ios.kt` with `beginBackgroundTask` to finish in-flight sync when the app is backgrounded.
- Android has reliable background sync via WorkManager with chained workers.
- Sync is event-triggered (current event change, new expense) with a 3-second debounce.
- Sequential chain: Currencies pull → Event push → Persons push → Persons pull → Expenses push → Expenses pull.

## Planned Improvements

### Layer 1: Finish In-Flight Sync on Backgrounding (`beginBackgroundTask`) — IMPLEMENTED

Each call to `pushAllEventInfo` in `EventsSyncManager.ios.kt` wraps the sync job with `UIApplication.beginBackgroundTaskWithName` / `endBackgroundTask`:

- Background task is requested before the coroutine job launches.
- On job completion (success, failure, or cancellation), `endBackgroundTask` is called from `invokeOnCompletion`. This is the single call site.
- If the OS expiration handler fires, it uses `runBlocking { cancelEventSync(eventId) }` to block until the job is cancelled and `invokeOnCompletion` has ended the background task. UIKit requires the task to be ended before the expiration callback returns.
- `cancelEventSync` does not call `endBackgroundTask` itself — `cancelAndJoin` guarantees `invokeOnCompletion` runs.
- `syncJobs` map tracks both the coroutine `Job` and the OS `UIBackgroundTaskIdentifier` per event via a `SyncJob` class.

No Info.plist or entitlement changes required.

### Layer 2: Periodic Background Refresh (`BGTaskScheduler`)

Use Apple's `BackgroundTasks` framework to periodically pull fresh data while the app is suspended.

**What it solves:** keeps expense data up-to-date even when the user hasn't opened the app for a while (e.g. other participants added expenses).

**Approach:**

- Register a `BGAppRefreshTask` at app launch via `BGTaskScheduler.shared.register(...)`.
- Add `fetch` to `UIBackgroundModes` in `Info.plist`.
- In the task handler, run the pull portion of the sync chain (currencies pull → persons pull → expenses pull for each known event).
- Schedule the next refresh at the end of each execution.
- Budget: ~30 seconds of wall-clock time; system decides actual scheduling frequency.

**Complexity:** Medium — requires Info.plist changes, task registration, and a pull-only sync path.

## Rejected Options

- **Silent push notifications:** requires server-side APNs infrastructure; delivery is throttled and unreliable. Not worth the complexity for this app's scale.
- **URLSession background transfers:** designed for large file transfers, overkill for the small JSON payloads in this app's sync chain.
