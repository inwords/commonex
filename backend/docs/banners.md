# In-app banners

HTTP: [`GET /user/banner`](../../docs/network-contracts.md#in-app-banner-caching).

Configure the migrated `banner` table using the application's PostgreSQL schema:

- `platform`: `Android` or `iOS`; release/debug builds share rules.
- `type`: `general_warning` or `update_required`; `text` must be nonblank.
- Inclusive build-code range: `min_version_code` defaults to `0`; `max_version_code = NULL` is unbounded.
- `enabled` defaults to `false`; an empty table shows no banner.
- Matching rules: `update_required` wins, then highest `id`. Editing a row preserves its priority.

```sql
-- Android builds below 124
INSERT INTO banner (platform, type, text, max_version_code, enabled)
VALUES ('Android', 'update_required', 'Please update CommonEx to continue.', 123, true)
RETURNING id;
-- All iOS builds
INSERT INTO banner (platform, type, text, enabled)
VALUES ('iOS', 'general_warning', 'Some features are temporarily unavailable.', true)
RETURNING id;
-- Edit or disable by returned ID
UPDATE banner SET text = 'Updated message' WHERE id = 1;
UPDATE banner SET enabled = false WHERE id = 1;
```

Checks from `backend/`: `npm test -- banner parse-client-version` and `npm run test:e2e -- banner`.
