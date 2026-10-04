# PostgreSQL/TypeORM expected errors: primary-source notes

Date: 2026-10-04

## Scope

This note records vendor-level facts and a local source audit needed to evaluate narrowly mapped PostgreSQL errors in the CommonEx backend. A database condition is classified as a business conflict only when its runtime path and domain context are proven.

The installed backend versions inspected locally are `typeorm@1.1.1` and `pg@8.23.0`.

## Stable PostgreSQL identifiers

PostgreSQL explicitly recommends branching on the five-character SQLSTATE rather than localized message text. It also reports associated database-object names in separate protocol fields where available. The relevant codes are:

| SQLSTATE | PostgreSQL condition | What the code proves |
| --- | --- | --- |
| `23502` | `not_null_violation` | A NOT NULL integrity constraint was violated. |
| `23503` | `foreign_key_violation` | A foreign-key integrity constraint was violated. |
| `23505` | `unique_violation` | A unique or primary-key constraint was violated. |
| `23514` | `check_violation` | A CHECK constraint was violated. |
| `40001` | `serialization_failure` | PostgreSQL aborted a transaction to prevent a serialization anomaly. |
| `40P01` | `deadlock_detected` | PostgreSQL detected a deadlock and aborted one participant. |
| `55P03` | `lock_not_available` | A requested lock was not available under fail-fast/timeout lock behavior. |

Source: [PostgreSQL Appendix A, Error Codes](https://www.postgresql.org/docs/current/errcodes-appendix.html).

The code alone identifies the database condition, not its domain meaning. In particular, an arbitrary `23505`, `23503`, `23514`, or `23502` is not automatically a business conflict. A safe bounded mapping also needs the specific operation and, where applicable, the expected constraint/object identity. Unknown codes and unexpected constraints should remain infrastructure/runtime errors.

## Error object fields

At the PostgreSQL protocol level, ErrorResponse field `C` is the non-localized SQLSTATE and is always present. Field `n` is the constraint name when the error is associated with a specific constraint. PostgreSQL warns that schema/table/column/type/constraint fields exist only for supported error kinds and that the presence of one does not imply the presence of another. Appendix A says separate object-name fields have complete coverage for class 23 integrity errors, but consumers should still use the field appropriate to the error: for example, a NOT NULL violation can identify a column without supplying a named constraint.

Sources: [PostgreSQL Error and Notice Message Fields](https://www.postgresql.org/docs/current/protocol-error-fields.html), [PostgreSQL Appendix A](https://www.postgresql.org/docs/current/errcodes-appendix.html).

The installed `pg@8.23.0` protocol parser maps those wire fields directly:

- `fields.C` to `DatabaseError.code`;
- `fields.n` to `DatabaseError.constraint`;
- object fields to `schema`, `table`, `column`, and `dataType`.

This was verified in `backend/node_modules/pg-protocol/src/parser.ts` and its `DatabaseError` declaration in `backend/node_modules/pg-protocol/src/messages.ts`. Upstream source: [node-postgres protocol parser](https://github.com/brianc/node-postgres/blob/master/packages/pg-protocol/src/parser.ts), [node-postgres error type](https://github.com/brianc/node-postgres/blob/master/packages/pg-protocol/src/messages.ts).

The installed `typeorm@1.1.1` PostgreSQL query runner catches the `pg` error and throws `new QueryFailedError(query, parameters, err)`. `QueryFailedError` retains the original error as `driverError` and copies its enumerable properties except `name` onto the wrapper. Consequently, at runtime PostgreSQL `code` and `constraint` are available both under `error.driverError` and as copied top-level properties, although `driverError` is the explicit typed TypeORM field and is the clearer vendor boundary.

This was verified in `backend/node_modules/typeorm/driver/postgres/PostgresQueryRunner.js` and `backend/node_modules/typeorm/error/QueryFailedError.js`. Matching upstream version source: [TypeORM 1.1.1 `PostgresQueryRunner`](https://github.com/typeorm/typeorm/blob/1.1.1/src/driver/postgres/PostgresQueryRunner.ts), [TypeORM 1.1.1 `QueryFailedError`](https://github.com/typeorm/typeorm/blob/1.1.1/src/error/QueryFailedError.ts).

## Retry semantics

PostgreSQL gives strong, narrow retry guidance:

- `40001` should be retried by rerunning the **complete transaction**, including the application logic that chose the statements and values. Retrying only the failed statement is insufficient.
- `40P01` may also be retried, again at the transaction boundary. PostgreSQL's locking documentation says a detected deadlock aborts one transaction and can be handled by retrying the aborted transaction.
- `23505` is retryable only in special concurrency patterns, such as application-selected keys racing after prior reads. PostgreSQL cautions that the same code commonly represents a persistent condition, so it must not receive a blanket retry policy.
- A retry is not guaranteed to succeed and may need a bounded multi-attempt policy.

Sources: [PostgreSQL Serialization Failure Handling](https://www.postgresql.org/docs/current/mvcc-serialization-failure-handling.html), [PostgreSQL Explicit Locking: Deadlocks](https://www.postgresql.org/docs/current/explicit-locking.html#LOCKING-DEADLOCKS).

For lock contention, `NOWAIT` makes a lock request fail immediately instead of waiting, while `lock_timeout` aborts a statement that waits too long for a lock. PostgreSQL assigns `55P03` to `lock_not_available`; its source uses that condition for failed row-lock acquisition. These facts do not establish a universal retry rule: whether contention is an expected "busy/conflict" response, an internal failure, or eligible for bounded retry depends on the operation's contract and transaction boundary.

Sources: [PostgreSQL `SELECT` locking clause (`NOWAIT`/`SKIP LOCKED`)](https://www.postgresql.org/docs/current/sql-select.html#SQL-FOR-UPDATE-SHARE), [PostgreSQL `lock_timeout`](https://www.postgresql.org/docs/current/runtime-config-client.html#GUC-LOCK-TIMEOUT), [PostgreSQL row-lock source using `ERRCODE_LOCK_NOT_AVAILABLE`](https://github.com/postgres/postgres/blob/master/src/backend/access/heap/heapam_handler.c).

## Implications for a bounded mapping

1. Match `QueryFailedError`, then inspect the PostgreSQL driver error's `code` and, for a constraint-specific contract, `constraint` (or the relevant object field). Do not parse `message` or `detail`.
2. Require both a proven operation path and a stable expected database object before turning an integrity violation into a domain error.
3. Keep unknown SQLSTATE values, unexpected constraints, and unrelated failures on the existing internal-error path.
4. Treat transaction retry as its own bounded policy. `40001` and possibly `40P01` justify whole-transaction retry; integrity and lock errors need operation-specific proof.

## Local runtime audit beyond `55P03`

This is source inspection of the installed versions and current application code, not a database reproduction. No additional expected HTTP/gRPC conflict was established by this audit; `55P03` is outside its scope. Dependency versions are pinned in the [lockfile (`pg`)](../../backend/package-lock.json#L10573) and [lockfile (`typeorm`)](../../backend/package-lock.json#L12258).

| Candidate | Confirmed local facts | Consequence for a bounded mapping |
| --- | --- | --- |
| `23505` | The migrations define primary keys, but no additional UNIQUE constraints. API event/user/expense IDs are generated with ULID; share tokens use 32 random bytes. | A generated-key collision can fail an INSERT, but no normal client-selected duplicate-key conflict was established. Do not infer a user conflict from any primary-key violation. |
| `23503` | The complete migration chain defines no foreign keys. Entity references such as `eventId`, `currencyId`, and `userWhoPaidId` are scalar columns. | No FK error path exists in the migration-defined schema. A missing referenced object is not evidence of `23503`. |
| `40001` | API transaction calls omit isolation; TypeORM sends `START TRANSACTION` and only sets isolation when configured. The only explicit application isolation is startup currency initialization with `REPEATABLE READ`. | API isolation depends on the database/session default, which was not inspected here. The startup setting alone does not prove a serialization failure in an API operation. No request mapping was established. |
| `40P01` | Idempotency takes one nonblocking advisory lock. Expense writes, v2 user addition, and event deletion request one event row with `NOWAIT`; cleanup holds its own nonblocking advisory lock and deletes expired records. | No concrete wait cycle between current API operations was established. General PostgreSQL deadlock support does not establish an expected conflict in these operations. |

Schema sources: [initial tables and primary keys](../../backend/migrations/default/1750591686449-init.ts#L6), [share-token table](../../backend/migrations/default/1767464629981-init.ts#L6), [idempotency table](../../backend/migrations/default/1776518011496-init.ts#L6), and the remaining [migration chain](../../backend/migrations/default). Representative scalar references: [expense entity](../../backend/src/frameworks/relational-data-service/postgres/entities/expense.entity.ts#L14), [user entity](../../backend/src/frameworks/relational-data-service/postgres/entities/user-info.entity.ts#L14). ID sources: [event](../../backend/src/domain/value-objects/event.value-object.ts#L13), [user](../../backend/src/domain/value-objects/user-info.value-object.ts#L13), [expense](../../backend/src/domain/value-objects/expense.value-object.ts#L13), [share token](../../backend/src/domain/value-objects/event-share-token.value-object.ts#L16).

The migration-defined primary-key names are `pk__currency__id`, `pk__event__id`, `pk__currency_rate__date`, `pk__expense__id`, `pk__user__id`, `pk__event_share_token__token`, and `pk__idempotency_keys__key` (the creation migrations above). In particular, `user_info` retains `pk__user__id`: its [rename migration](../../backend/migrations/default/1765728743776-RenameUserTable.ts#L4) renames only the table. The [naming strategy](../../backend/src/frameworks/relational-data-service/postgres/postgres-naming-strategy.ts#L42) would generate a different name for a newly created `user_info` table; generated metadata must not substitute for the applied migration name. These names were established from migrations, not a live catalog query.

### Duplicate-key paths already have operation-specific behavior

The same idempotency key is checked before and after `pg_try_advisory_xact_lock(hashtextextended(key, 0))`; the successful operation and its idempotency INSERT run in that transaction. A competing request that cannot acquire the advisory lock returns `IdempotencyRequestInProgressError`, rather than relying on a `23505` catch. A matching stored request replays its response; a different operation/body returns `IdempotencyHashMismatchError`. Sources: [transaction and double check](../../backend/src/usecases/shared/idempotency.usecase.ts#L102), [advisory lock](../../backend/src/frameworks/relational-data-service/postgres/repositories/idempotency-key.repository.ts#L53), [hash comparison](../../backend/src/usecases/shared/idempotency.usecase.ts#L203). Their current HTTP statuses are [409 and 422](../../backend/src/api/http/filters/business-error.filter.ts#L16); gRPC uses [ABORTED and FAILED_PRECONDITION](../../backend/src/api/grpc/filters/grpc-status.map.ts#L16). This is normal same-key concurrency handling, not a general proof that `23505` is unreachable under other writers or database settings.

Currency-rate insertion uses [ON CONFLICT (`date`) DO UPDATE](../../backend/src/frameworks/relational-data-service/postgres/repositories/currency-rate.repository.ts#L102), so an existing date already has explicit update behavior. Currency initialization instead [reads supported codes and inserts missing currencies](../../backend/src/frameworks/frameworks.layer.ts#L18); `currency.code` has [no uniqueness decorator](../../backend/src/frameworks/relational-data-service/postgres/entities/currency.entity.ts#L10), and its migration adds no uniqueness constraint. Concurrent startup is not a proven `23505` conflict on a currency code. Similarly, share-token creation [reads an existing active token and otherwise inserts a random token](../../backend/src/usecases/users/v2/create-event-share-token-v2.usecase.ts#L33); the event/expiry index is [nonunique](../../backend/migrations/default/1790502730841-init.ts#L14), so two active tokens for one event do not imply `23505`.

### Exact propagation and retry boundary

For example, HTTP [POST `/user/event`](../../backend/src/api/http/user/user.controller.ts#L38) and gRPC [CreateEvent](../../backend/src/api/grpc/user/user.controller.ts#L74) both call [SaveEventUseCase](../../backend/src/usecases/users/save-event.usecase.ts#L37), which enters [IdempotencySharedUseCase's transaction](../../backend/src/usecases/shared/idempotency.usecase.ts#L102) and executes [event and user INSERTs](../../backend/src/usecases/users/save-event.usecase.ts#L57). Those repositories await TypeORM execution without catching these SQLSTATEs ([event](../../backend/src/frameworks/relational-data-service/postgres/repositories/event.repository.ts#L61), [user](../../backend/src/frameworks/relational-data-service/postgres/repositories/user-info.repository.ts#L52)). Expense creation has the same boundary: [HTTP](../../backend/src/api/http/user/user.controller.ts#L123), [gRPC](../../backend/src/api/grpc/user/user.controller.ts#L147), [use case](../../backend/src/usecases/users/save-event-expense.usecase.ts#L43), [repository](../../backend/src/frameworks/relational-data-service/postgres/repositories/expense.repository.ts#L52).

The installed [PostgresQueryRunner](../../backend/node_modules/typeorm/driver/postgres/PostgresQueryRunner.js#L185) awaits `pg.query` and wraps a query rejection in `QueryFailedError`; its [wrapper](../../backend/node_modules/typeorm/error/QueryFailedError.js#L10) retains `driverError`. The installed [EntityManager.transaction](../../backend/node_modules/typeorm/entity-manager/EntityManager.js#L66) invokes the callback once, commits on success, attempts rollback on failure, and rethrows the original failure. It has no retry loop. CommonEx [binds that transaction method directly](../../backend/src/frameworks/relational-data-service/postgres/relational-data-service.ts#L32), and the cited use cases add no database retry. The [PostgresQueryRunner isolation branch](../../backend/node_modules/typeorm/driver/postgres/PostgresQueryRunner.js#L119) explains why unspecified API isolation is not evidence of `SERIALIZABLE`; the [startup provider](../../backend/src/frameworks/frameworks.layer.ts#L42) awaits the `REPEATABLE READ` initializer before returning its dependency, so an initializer failure is a bootstrap failure rather than an HTTP/gRPC response.

If an unmapped query failure reaches these controllers, their awaited use-case calls reject directly. The global [HTTP filter registration](../../backend/src/app.factory.ts#L17) and [InternalErrorFilter](../../backend/src/api/http/filters/internal-error.filter.ts#L16) produce HTTP 500 with `{statusCode: 500, code: "B4007", message: "Internal server error"}`. The controller-scoped [gRPC filter registration](../../backend/src/api/grpc/user/user.controller.ts#L58), [GrpcInternalErrorFilter](../../backend/src/api/grpc/filters/grpc-internal-error.filter.ts#L25), and [status map](../../backend/src/api/grpc/filters/grpc-status.map.ts#L13) produce gRPC `INTERNAL`, details `Internal server error`, and metadata `error-code: B4007`. Both retain the exception in [internal logging](../../backend/src/api/filters/internal-error.ts#L11). Neither response includes SQLSTATE or constraint details.

Lock/cleanup sources for the reachability limits above: [expense event lock](../../backend/src/usecases/users/save-event-expense.usecase.ts#L55), [v2 user-addition event lock](../../backend/src/usecases/users/v2/save-users-to-event-v2.usecase.ts#L48), [event-deletion lock](../../backend/src/usecases/users/delete-event.usecase.ts#L32), [cleanup transaction](../../backend/src/usecases/cron/cleanup-expired-data.usecase.ts#L26). No additional SQLSTATE mapping, blanket retry policy, or production change follows from this source audit.
