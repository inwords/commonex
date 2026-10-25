# Web analytics

CommonEx web uses Yandex Metrica for optional traffic analysis and browser funnels. Android continues using AppMetrica; the two dashboards do not automatically share visitor identity or form one funnel.

Design and exact goal contract: [web analytics design](../../docs/product/2026-10-web-analytics-design.md). Provider sources: [research](../../docs/research/2026-10-web-analytics-provider-research.md).

## Configuration and activation

The feature is disabled without both build-time values:

- `NEXT_PUBLIC_YANDEX_METRICA_ID`: positive integer ID of a dedicated **website** counter, not the Android API key.
- `NEXT_PUBLIC_ANALYTICS_ALLOWED_HOSTS`: comma-separated exact hostnames, without scheme, port, wildcards or paths. Only explicitly listed production hosts can load the SDK. Development builds never load it.

GitHub repository variables `WEB_YANDEX_METRICA_ID` and `WEB_ANALYTICS_ALLOWED_HOSTS` supply these values to the frontend Docker build. They are public configuration, not API secrets. The backend receives no analytics build arguments. Next.js embeds public values into the static bundle: changing container runtime environment does not configure an existing image.

Before setting production values:

1. Create a dedicated web counter under the CommonEx owner account. Confirm domain/allowed hosts. Use a separate QA counter for testing and editor access for report setup.
2. Disable Webvisor/session replay, click/link maps, form analysis, automatic goals, ecommerce, advertising integrations, cross-device enrichment and Tag Manager in the counter where available. Code also requests disabled automatic features. Do not enable SDK extensions from the counter UI.
3. Create each JS goal listed below as a separate goal, not just a step inside a composite goal.
4. Confirm the actual provider data retention/deletion capabilities, access control and cookie behavior; record the chosen values in your release evidence. Do not assume an application-configured TTL controls provider storage. Review the published RU/EN privacy text before activation.
5. Run the browser checklist below against the QA counter. Confirm goals and sources in its UI; local network assertions alone do not prove report delivery.
6. Only then build the production frontend with its counter ID and hosts and deploy using the normal release flow.

A visitor must also allow analytics. Before permission, after decline, in development, or with missing/invalid configuration, no third-party analytics script is loaded and no product events are queued. The website remains usable. Analytics settings are accessible in the footer. Revoking permission stops local sending and reloads without the SDK. It does not delete historical provider data or other-domain cookies; visitors can clear browser site data and contact support.

Landing campaign context lives only in page memory; queued messages are limited to 50 with a 30-second lifetime. No analytics export or raw SDK request log is stored by the app. Activities performed before consent are not replayed; consequently a visitor consenting only after joining may have a partial funnel. This coverage limitation must remain visible when interpreting conversions.

## Goals

`trip_create_opened`, `trip_created`, `trip_create_failed`, `invitation_requested`, `invitation_copied`, `invitation_failed`, `invitation_opened`, `join_started`, `join_succeeded`, `join_failed`, `participant_claimed`, `expense_opened`, `expense_submitted`, `expense_failed`, `settlement_viewed`, `settlement_recorded`, `settlement_failed`, `store_link_clicked`.

All send `schema_version=1`, `platform=web`; only event-specific enum values from the design are accepted. No IDs of trips/users/expenses, names, financial amounts, descriptions, credentials or free-text errors are sent. A payer is not necessarily the person acting. Selecting a participant is a convenience claim, not identity verification.

`join_succeeded` means event access succeeded; `participant_claimed` is the later explicit choice of participant. Creation is not a join. Retry attempts produce one final success or failure, not multiple conversions. `settlement_viewed` currently means opening the debts tab, and `settlement_recorded` means saving a refund. A store click does not mean installation. Do not send events for unimplemented trip-close/edit/offline-sync flows.

## Campaign links and reports

External campaign example: `https://commonex.ru/?utm_source=telegram&utm_medium=messenger&utm_campaign=travel_launch`.

The whitelist is in `analytics-service.ts`: extend registered public campaign/content codes there when launching another campaign. Unknown values become `other`; arbitrary UTM text is never forwarded. `utm_term` and unlisted query/hash parameters are removed. Dynamic event paths become `/event`. Only the first hit retains allowed campaign tags; Metrica reads source tags from the first view in a visit.

Copied invitation links use `commonex/referral/trip_invite`, independently of the organizer's acquisition campaign. Clipboard sharing does not reveal the receiving messenger. Missing UTM/referrer may remain direct/unknown.

Create these reports with editor access:

- Traffic source/medium/campaign → visits, `trip_created`, `join_succeeded`, `expense_submitted`.
- Organizer: home view → `trip_create_opened` → `trip_created` → `participant_claimed` → `expense_submitted`. Sharing is an optional branch, not a prerequisite for the first expense.
- Invitee: `invitation_opened` → `join_started` → `join_succeeded` → `participant_claimed` → `expense_submitted`; window 48 hours, token/manual entry segments where applicable.
- Expense entry: `expense_opened` → `expense_submitted`, with failure-reason report separately.

Keep the chosen attribution model and funnel window in the saved report description. These are visitor/browser funnels; steps may belong to different trips. They cannot prove Collaboratively Activated Trips, two real people, a 48-hour group cohort or a common Android/web identity. Those require a separate backend/mobile correlation design.

## Privacy and browser verification

The SDK is loaded only after permission, with an official-style `ym` bootstrap queue. The counter ID and hostname configuration are embedded in the application bundle at build time. A static SDK script tag in exported HTML would load before consent; loading remains owned by the single `analyticsService`. It initializes only after a safe first pageview is available. `defer:true` alone is insufficient: the current official SDK emits technical `nohit` requests containing the original URL unless its initial `url` and `referrer` are explicitly overridden. We seed both with the sanitized landing view, send explicit safe `hit` URL/referrer/title, and disable automatic features including `disableYtm`.

**Compatibility caveat:** `url`/`referrer` are supported by the current [official SDK source](https://mc.yandex.ru/metrika/tag.js), but absent from the current public initialization table. Live network regression checks are a release gate, including after counter settings or SDK changes. Never bypass a failing gate by enabling production collection anyway.

Use synthetic data only. Check script, init/nohit, hit, reachGoal, background requests, URL/body and HTTP Referer:

1. `/event/SYNTHETIC_PRIVATE_ID?token=SYNTHETIC_INVITE_SECRET#SYNTHETIC_HASH`, sensitive title/form fields and a credential-bearing referrer. No marker may appear in analytics requests.
2. First registered campaign survives router transitions and consent delay. Later views have no marketing query. StrictMode effect repetition is not a second view; back/forward is.
3. Success after retries creates one goal; final failure one classified failure. Creating a trip does not create a join goal. Clipboard failure is not copied success.
4. No consent/decline → zero analytics traffic; revoke/reload → no further SDK loading. Adblock/SDK network failure/storage disabled must not prevent business operations.
5. Two browser contexts independently perform organizer and invitee flows; inspect actual report data rather than attempting to join these contexts into one visitor.

## Validation record

Implementation started 23 October 2026 from `origin/main` `c3a64f02` on `feat/web-analytics`.

- Baseline native tests: 14 passing; implementation suite: 31 passing. Production builds with a test ID and with empty configuration succeeded; the frontend Docker image with test build arguments also built successfully.
- Actual entity services tested for retry outcomes, join error propagation and analytics failure isolation; existing tests remain in the suite.
- Production browser probe reproduced raw URL leakage in SDK technical requests with `defer:true`; safe init URL/referrer probe removed those markers, including goal referrer. Full integration regression: 0 sensitive marker leaks in 13 SDK requests, including technical init/nohit and a participant goal; 0 analytics requests before consent. Decline and SDK blocking did not prevent opening the creation form. A pre-existing LanguageStore crash with blocked storage was reproduced and fixed; the regression test passes.
- QA/production counter ownership, goal receipt and report configuration are **not verified** without account access. No production counter has been configured or activated by this change.

## Rollback

Remove `WEB_YANDEX_METRICA_ID`, rebuild and deploy the frontend. Runtime environment changes do not remove code/configuration from an existing static artifact, and deployment does not stop already open tabs. Verify any provider-side emergency disable mechanism under the actual owner account before rollout; do not assume one is available. Visitors can revoke permission locally immediately.

## Module structure

`src/6-shared/analytics/analytics-service.ts` owns SDK loading, configuration, consent, navigation, URL minimization, failure classification and event delivery. Callers use the `analyticsService` singleton. `analytics-event.ts` holds the event contract and runtime parameter allowlist. The two React widgets remain separate because they handle routing and consent UI; neither owns another analytics service. All web conditionals require braces through ESLint `curly`.
