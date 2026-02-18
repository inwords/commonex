# CommonEx Domain Glossary

This document defines core domain terms and the primary sources of truth for their shape and meaning.

## Source of Truth

- Backend domain models and enums live in `backend/src/domain/entities/`.
- Backend domain defaults and ID generation live in `backend/src/domain/value-objects/`.
- Backend API contracts live under `backend/src/api/` and `backend/src/expenses.proto`.
- Mobile (Android/iOS KMM) domain models live under `android/shared/feature/events/domain/model/` and
  `android/shared/feature/expenses/domain/model/`.
- Web domain models live under `web/src/5-entities/**/types/` and `web/src/5-entities/**/constants.ts`.
- Keep client models aligned with backend domain and API contracts.

## Core Entities (Backend Canonical)

### Event

- A group or context that expenses belong to.
- Fields: id, name, currencyId, pinCode, createdAt, updatedAt, deletedAt.
- `deletedAt` is soft delete; non-null means the event is considered deleted.
- ID and timestamps default to new values (ULID + now) via value objects.
- Canonical model: `backend/src/domain/entities/event.entity.ts`.

### User Info (Person)

- A participant associated with an event.
- Fields: id, name, eventId, createdAt, updatedAt.
- IDs default to ULID; timestamps default to now.
- Canonical model: `backend/src/domain/entities/user-info.entity.ts`.

### Expense

- A financial record within an event. Type is `expense` or `refund`.
- Fields: id, description, userWhoPaidId, currencyId, eventId, expenseType, splitInformation, createdAt, updatedAt.
- `createdAt` is optional input and defaults to now.
- Canonical model: `backend/src/domain/entities/expense.entity.ts`.

### Split Information

- Per-user share of an expense.
- Fields: userId, amount, exchangedAmount.
- `exchangedAmount` is in the event currency; it equals `amount` when currencies match.
- Canonical model: `backend/src/domain/entities/expense.entity.ts` (ISplitInfo).

### Currency

- Supported currency code used by events and expenses.
- Fields: id, code, createdAt, updatedAt.
- Codes: EUR, USD, RUB, JPY, TRY, AED.
- Canonical model: `backend/src/domain/entities/currency.entity.ts`.
- Source list: `backend/src/constants.ts` (CURRENCIES_LIST).

### Currency Rate

- Exchange-rate snapshot for a given UTC date (YYYY-MM-DD).
- Fields: date, rate (map of currency code to number), createdAt, updatedAt.
- Rates are fetched from Open Exchange Rates with base USD.
- Canonical model: `backend/src/domain/entities/currency-rate.entity.ts`.

### Event Share Token

- Temporary access credential for event sharing.
- Fields: token, eventId, expiresAt, createdAt.
- Token default: 32 random bytes encoded as hex (64 chars).
- Default expiry: 14 days from creation.
- Existing active token is reused when creating a new share token for the same event.
- Exchanged for permanent PIN code upon successful join via token.
- Canonical model: `backend/src/domain/entities/event-share-token.entity.ts`.

## Domain Rules and Flows (Backend)

### Event Access and Lifecycle

- A valid event must exist and not be soft-deleted.
- Pin codes are exactly 4 characters for event access.
- V1 APIs use pin codes for event info and mutations; V2 adds share tokens for event info access.
- Deleting an event sets `deletedAt` and updates `updatedAt`.

### User Management

- Users (participants) are always scoped to an event and created via add-users endpoints.
- User creation assigns a new ULID and timestamps.

### Expense Creation and Conversion

- Expenses may be created in any supported currency.
- If expense currency matches the event currency: `exchangedAmount` equals `amount`.
- If currencies differ:
    - Get currency rates for the expense date (UTC YYYY-MM-DD, `createdAt` if provided, otherwise now).
    - Compute `exchangeRate = eventCurrencyRate / expenseCurrencyRate`.
    - `exchangedAmount = round(amount * exchangeRate, 2)`.
- Missing currencies or rates yield errors (see Error Codes).

### Currency Rates

- Daily rates are fetched via cron and can be fetched on demand via devtools.
- Rates are keyed by UTC date and used for conversion by date.
- Persistence is idempotent by UTC date: repeated saves for the same date upsert the row (update `rate` and
  `updatedAt`).

## Mobile Domain (Android and iOS - KMM Shared)

### Identity and Sync

- Mobile domain entities carry a local `id: Long` and optional `serverId: String?` for offline-first sync.
- Event, Person, and Currency all follow this pattern in KMM models.

### Events and People

- `EventDetails` bundles the current event, currencies, persons, and primary currency.
- Event creation generates a 4-digit pin code and creates the initial owner plus other persons.
- Participants can be added to existing events via the "Add participants" menu option (Android) or modal (Web).
- New participants are stored locally immediately (offline-first) and synced to server via `EventPersonsPushTask`.
- Joining an event uses `serverId` + pin code or share token; errors map to invalid access code, invalid token, token
  expired, not found, or gone.
- Mobile deeplinks with `?token=` or `?pinCode=` prefill and auto-trigger join.
- Event share tokens include `token` and `expiresAt` and are requested with a pin code.
- Share token generation falls back to PIN-based link with warning if network request fails (offline mode).

### Expenses and Splits

- `Expense` includes the payer (`person`), currency, type, timestamp, and split list.
- `ExpenseSplitWithPerson` holds `originalAmount` (expense currency) and `exchangedAmount` (event currency).
- `ExpenseType` uses `Spending` and `Replenishment` (maps to backend `expense` and `refund`).
- Equal split divides by number of selected persons; custom split uses explicit amounts.
- Reverting an expense creates a new expense with inverted amounts and opposite type.

### Debts

- Debts are derived from splits in event primary currency.
- Accumulated debts are grouped by debtor and creditor.
- Barter debts subtract opposite directions and drop amounts below 0.01.

### Currency Exchange (Mobile)

- Mobile conversion uses `CurrencyExchanger`, currently based on a USD rate map as a placeholder.
- Exchange is skipped when the expense currency matches the event primary currency.

## Web Domain

### Events and Sharing

- `Event` includes id, name, currencyId, users, and pinCode.
- Event info can be fetched with either pin code or share token (V2).
- Share links append `?token=...` to `/event/{id}`; tokens are valid for 14 days.

### Expenses and Refunds

- `ExpenseType` matches backend values: `expense` and `refund`.
- Split option `1` is equal split across all users; option `2` is manual amounts per user.
- Refunds are created as `ExpenseType.Refund` with a single split for the receiver.
- Web debt summary sums `exchangedAmount` owed to each payer and subtracts refunds paid by the current user.

### Currencies (Web)

- Web currency codes are EUR, USD, RUB, JPY, TRY, with an ID to code mapping in `CURRENCIES_ID_TO_CURRENCY_CODE`.
- AED exists in backend domain but is not listed in web constants; align if adding AED to the UI.

## API Surfaces (Domain-Related)

- HTTP V1: `/user` routes (pin-code based, but not everywhere).
- HTTP V2: `/v2/user` routes (pin code + share token for event info).
- gRPC: `backend/src/expenses.proto` defines UserService operations.

## Error Codes (Domain)

- B4001 EVENT_NOT_FOUND
- B4002 EVENT_ALREADY_DELETED
- B4003 EVENT_INVALID_PIN
- B4004 CURRENCY_NOT_FOUND
- B4005 CURRENCY_RATE_NOT_FOUND
- B4008 INVALID_TOKEN
- B4009 TOKEN_EXPIRED
- See `backend/src/domain/errors/` for full mapping and HTTP status codes.
