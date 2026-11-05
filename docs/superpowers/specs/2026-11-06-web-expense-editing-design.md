# Web expense editing

## Intent and scope

Implement expense editing in the web client on a branch from current main, matching backend PR #344 (head 21ea0d4c). Backend code remains unchanged. Reuse the existing feature-sliced layers: API transport and orchestration in expense services, observable data in expense store, form in features, and dialog composition in widgets.

## Approach

Extend ExpenseForm and ExpenseDetailsModal. This reuses the existing amount, payer, currency and split controls. A separate edit form would duplicate these controls and calculations. Compensating expenses do not implement the backend revision contract. No new dependency is needed.

## Availability

NEXT_PUBLIC_EXPENSE_EDITING_ENABLED must equal the literal string `true`; otherwise editing is unavailable and existing API routes and creation behavior remain unchanged. This is a public build-time switch, not authorization. Document rebuilding the static export and pass the switch as a Docker build argument.

Keep existing v2 expense reads/creation: backend PR #344 adds version and updatedAt to those responses too. Use v4 only for revision writes. Offer editing only for an event whose version is 2 and an expense with a valid version. Do not silently upgrade legacy events. Backend enforces PIN and event eligibility.

Approved: enabled builds create new events with version 2. Old mobile clients cannot access these events; legacy events remain unchanged.

## Interaction

Open existing expense details read-only. Eligible expenses show Edit. Entering editing mounts a fresh, prefilled form with Save changes and Cancel. Cancellation discards the draft. Disable duplicate submission and dismissal while saving. Failed requests retain the draft and display a localized message. Successful saving replaces the existing store entry by ID, updates the selected expense, and therefore recalculates totals and debts without adding a second expense or triggering expense-created analytics/onboarding.

Start editing with explicit saved participant amounts, even if all saved shares are equal: current event membership may differ from membership at creation. Preserve saved exchange amounts and custom-rate intent when financial inputs do not change. Track whether the rate was explicitly edited; do not infer custom-rate intent by comparison with today’s rate. For automatic conversion send isCustomRate=false, letting the backend use historical rates at createdAt. For explicit custom conversion send isCustomRate=true and exchangedAmount for every split. Do not replace a saved historical rate with today's automatic rate on mount. Allow changing description, payer, currency and distribution using existing controls. In manual mode the total comes from the split amounts; ensure the displayed total follows them rather than accepting an independent total with no effect. Initialize manual split state before mounting the form, so the existing equal-split effect cannot delete saved shares. Preserve original createdAt (date editing and history browsing are outside this change).

## Backend contract

POST /v2/user/event/:eventId/expenses with pinCode returns latest snapshots including version after backend PR #344 is deployed.

POST /v4/user/event/:eventId/expense/:id/revisions with pinCode, expectedVersion, description, userWhoPaidId, currencyId, splitInformation, createdAt, and optional isCustomRate creates a new revision. Do not send expenseType to the revision endpoint. Generate one idempotency key per save attempt and retain it across automatic retries. Retry only transient failures, never validation or version conflicts.

On a version conflict, do not overwrite server data or silently resubmit with a newer version. Preserve the draft, explain the conflict, and offer explicit reload of the latest snapshot before editing again. Handle unsupported events, missing expenses and invalid participants with localized messages.

## Validation

Use existing Node test infrastructure to verify flag-off behavior, route and payload contracts, stable idempotency across retries, version conflicts and retained state, replacing rather than appending revisions, stored split/rate preservation, and equal/manual/percentage edits. Run the full web test suite, lint, TypeScript check and production build. Check both flag settings. Exercise the dialog through a browser if browser tooling is available. Backend PR is not merged into main: integration validation must identify this dependency, and frontend tests must exercise the PR's request/response contract explicitly.

## Constraints

No backend implementation, legacy-event migration, refund-editing UI, new analytics payloads or unrelated cleanup. Keep existing creation behavior unchanged with the switch off. Keep secrets out of public environment variables. If external font loading still blocks the build, report that independently of application correctness and do not weaken TLS validation.
