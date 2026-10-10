# Fail-safe transaction seam для backend repositories

Дата исследования: 2026-10-10. Scope ограничен transaction context между use cases/framework composition и PostgreSQL repositories. HTTP/gRPC contracts, error/result policy, SQLSTATE mapping, retry policy, query shape, lock semantics и schema не рассматриваются и не меняются.

## Утверждённый вариант реализации

После сравнения пользователь выбрал вариант 3 и разрешил реализацию. Разделы ниже сохраняют первоначальное исследование и рекомендацию варианта 2 как историю выбора; актуальный дизайн следующий:

- `transaction` передаёт готовый `ITransaction`, содержащий обязательный opaque `ctx`; caller больше не формирует `{ctx: manager}`. Generic, позволяющий caller выбрать тип manager, удаляется; isolation level выражен нейтральным union.
- PostgreSQL framework создаёт token и регистрирует его в `WeakMap` вместе с callback `EntityManager` и владельцем `DataSource`. Token сохраняется при `{...trx, lock, onLocked}`. После завершения callback token инвалидируется в `finally`.
- Один framework resolver проверяет registration, owner и active/unreleased `QueryRunner`. Переданный malformed, raw-manager, foreign либо завершённый handle вызывает `TypeError` до SQL. Только optional `trx === undefined` разрешает global repository.
- Все семь repository adapters передают полный `trx` в resolver; transaction-only `tryAcquireLock` использует обязательную проверку того же resolver.
- `RelationalDataService` раскрывает нейтральную `tryAcquireTransactionLock(key, trx): Promise<boolean>` для двух cron advisory locks. Их существующий SQL сохраняется, TypeORM imports и raw `query` уходят из use cases. Idempotency repository сохраняет свой существующий SQL и `queryDetails`.
- Пять transaction roots получают готовый `trx`; остальные mutation use cases сохраняют нынешние repository calls, forwarding и lock options.
- Unit tests проверяют unforgeability, spread, ownership/lifetime, malformed contexts и отсутствие SQL; repository contract tests покрывают все trx-capable methods. DB-backed tests сохраняют lock/rollback assertions и добавляют проверку транзакционного чтения/записи. Тесты запускает пользователь.

Scope: domain transaction types/port, framework transaction implementation/resolver, семь repositories, пять transaction roots и связанные tests. HTTP/gRPC, Result/exception policy, error codes, query shapes, locks и schema сохраняются. `backend/src/main.ts` и `.playwright-mcp/` исключены из изменений; staging и commit запрещены.

Implementation Readiness: PASS для утверждённого opaque-handle варианта. Проверка исполнения и её результаты будут сообщены отдельно; исходный PASS ниже относится к первоначальному bounded-дизайну.

## Executive summary

Текущий runtime path небезопасен: `RelationalDataService.transaction` является прямой ссылкой на `DataSource.transaction`, callback получает TypeORM `EntityManager`, caller заворачивает его в `{ctx}`, а 29 repository methods преобразуют `ctx` в manager через `instanceof EntityManager`. Если transaction object существует, но `ctx` отсутствует или имеет неверный runtime type, тот же код выбирает глобальный `DataSource` и выполняет SQL вне ожидаемой transaction; ещё один public method делегирует в такой method и наследует поведение ([domain type](../../backend/src/domain/abstracts/relational-data-service/types.ts#L1), [binding](../../backend/src/frameworks/relational-data-service/postgres/relational-data-service.ts#L29), [типичный repository](../../backend/src/frameworks/relational-data-service/postgres/repositories/event.repository.ts#L21)). Это противоречит требованию TypeORM использовать внутри transaction именно переданный callback manager ([TypeORM Transactions](https://typeorm.io/docs/transactions/)).

Полный поиск production и test call sites не нашёл легитимных `{}`, `{ctx: undefined}`, fake context или context другого типа. Все production contexts создаются из callback `transaction`; все найденные test contexts также являются callback `EntityManager`. Поэтому можно сделать seam fail-safe без migration path для существующего caller.

Минимальный bounded-дизайн: сделать `ctx` обязательным внутри опционального `ITransaction`, централизовать runtime validation и выбор `Repository` в существующем framework `BaseRepository`, а семь PostgreSQL adapters направить через эту точку. Отсутствующий argument (`trx === undefined`) продолжает использовать `DataSource`; любой переданный object обязан содержать transaction-scoped `EntityManager` этого же `DataSource` с активным `QueryRunner`, иначе resolver бросает обычный programmer/infrastructure `TypeError` до создания query builder и SQL (async repository method возвращает rejected Promise). Новый business error/code не нужен.

## 1. Текущий runtime path

1. Domain объявляет `ITransaction.ctx?: unknown` и отдельно повторяет тот же optional `ctx` в `ITransactionWithLock`; `IRelationalDataService.transaction` скрывает manager за unconstrained generic ([types](../../backend/src/domain/abstracts/relational-data-service/types.ts#L1)).
2. PostgreSQL adapter присваивает `this.transaction = this.dataSource.transaction.bind(this.dataSource)`, не добавляя wrapper или validation ([relational-data-service.ts](../../backend/src/frameworks/relational-data-service/postgres/relational-data-service.ts#L29)). Значит runtime value callback — TypeORM `EntityManager`.
3. Production создаёт `{ctx}` в пяти transaction roots:
   - currency bootstrap с `REPEATABLE READ` ([frameworks.layer.ts](../../backend/src/frameworks/frameworks.layer.ts#L18));
   - data-retention cron ([cleanup-expired-data.usecase.ts](../../backend/src/usecases/cron/cleanup-expired-data.usecase.ts#L25));
   - currency-rate cron ([fetch-daily-currency-rates.usecase.ts](../../backend/src/usecases/cron/fetch-daily-currency-rates.usecase.ts#L20));
   - delete event ([delete-event.usecase.ts](../../backend/src/usecases/users/delete-event.usecase.ts#L31));
   - shared idempotency transaction, которая передаёт один `trx` в пять mutation flows ([idempotency.usecase.ts](../../backend/src/usecases/shared/idempotency.usecase.ts#L95), [save event](../../backend/src/usecases/users/save-event.usecase.ts#L37), [save V1 expense](../../backend/src/usecases/users/save-event-expense.usecase.ts#L43), [save V1 users](../../backend/src/usecases/users/save-users-to-event.usecase.ts#L36), [save V2 expense](../../backend/src/usecases/users/v2/save-event-expense-v2.usecase.ts#L58), [save V2 users](../../backend/src/usecases/users/v2/save-users-to-event-v2.usecase.ts#L36)).
4. Каждый обычный PostgreSQL repository method вычисляет `const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined`, затем private `getRepository` выбирает manager repository либо `dataSource.getRepository` ([event example](../../backend/src/frameworks/relational-data-service/postgres/repositories/event.repository.ts#L21), [selection](../../backend/src/frameworks/relational-data-service/postgres/repositories/event.repository.ts#L91)). Из-за optional chaining состояния «argument отсутствует» и «argument передан, но context invalid» схлопнуты в один `undefined`.
5. Исключение — `idempotencyKey.tryAcquireLock`: argument обязателен, неверный manager уже отвергается, а raw query выполняется через `trx.ctx.query` ([repository](../../backend/src/frameworks/relational-data-service/postgres/repositories/idempotency-key.repository.ts#L53)). Ещё два прямых `EntityManager.query` находятся в cron use cases для transaction-scoped advisory locks ([cleanup](../../backend/src/usecases/cron/cleanup-expired-data.usecase.ts#L26), [currency rates](../../backend/src/usecases/cron/fetch-daily-currency-rates.usecase.ts#L21)). `DataSource.query` в health/schema initialization не получает `trx` и не относится к этому seam ([relational-data-service.ts](../../backend/src/frameworks/relational-data-service/postgres/relational-data-service.ts#L55)).

Нужные три состояния:

| Runtime input | Требуемое поведение |
|---|---|
| `trx === undefined` (argument опущен либо optional forwarding дал `undefined`) | Использовать глобальный `DataSource`; это штатный non-transactional вызов. |
| `trx !== undefined`, `ctx` — `EntityManager` того же `DataSource`, а его `QueryRunner` существует, не освобождён и имеет активную transaction | Использовать только этот manager и его repository. |
| `trx !== undefined`, но `ctx` отсутствует/`undefined`/не `EntityManager`, принадлежит другому `DataSource`, является global manager либо уже не имеет активной transaction | Бросить programmer/infrastructure exception до query builder/SQL; global fallback запрещён. |

## 2. Inventory production repositories

Все методы, кроме `idempotencyKey.tryAcquireLock`, имеют optional transaction в domain port. «Риск» ниже означает достижимый silent fallback при ошибочном non-`undefined` argument, а не найденный production incident. Во всех семи adapter specs есть DB-backed functional/query snapshot coverage, но fail-fast invalid-context coverage отсутствует.

| Repository / methods | Без transaction | Текущая обработка `trx.ctx` | Production callers | Риск silent fallback | Существующие тесты |
|---|---:|---|---|---|---|
| `CurrencyRate`: `findByDate`, `findSupportedCurrenciesWithRatesVersionByDate`, `findAll`, `insert` ([adapter](../../backend/src/frameworks/relational-data-service/postgres/repositories/currency-rate.repository.ts#L23), [port](../../backend/src/domain/abstracts/relational-data-service/repositories/currency-rate.repository.ts#L5)) | Да, все | Четыре одинаковых `instanceof` checks; invalid становится `DataSource` ([lines 27, 43, 84, 100](../../backend/src/frameworks/relational-data-service/postgres/repositories/currency-rate.repository.ts#L23)) | `findByDate`/version через `SupportedCurrencyService`; direct devtools read без trx; `insert` через shared fetch, cron передаёт valid trx, devtools — `undefined`; у `findAll` production caller нет ([service](../../backend/src/frameworks/supported-currency-service/supported-currency-service.ts#L23), [fetch/save](../../backend/src/usecases/shared/fetch-and-save-currency-rate.usecase.ts#L24), [devtools](../../backend/src/usecases/devtools/get-currency-rate.usecase.ts#L17)) | Да для всех четырёх | Functional/snapshots для всех четырёх, только без trx ([spec](../../backend/src/frameworks/relational-data-service/postgres/repositories/__tests__/currency-rate.repository.spec.ts#L30)); cron test проверяет передачу object context, но не invalid context ([test](../../backend/src/usecases/cron/__tests__/fetch-daily-currency-rates.usecase.test.ts#L88)). |
| `Currency`: `findById`, `findSupportedById`, `findAll`, `findAllSupported`, `insert` ([adapter](../../backend/src/frameworks/relational-data-service/postgres/repositories/currency.repository.ts#L22), [port](../../backend/src/domain/abstracts/relational-data-service/repositories/currency.repository.ts#L4)) | Да, все | Четыре direct checks; `findAllSupported` делегирует `trx` в `findAll` ([adapter](../../backend/src/frameworks/relational-data-service/postgres/repositories/currency.repository.ts#L22)) | `findSupportedById`/`findAllSupported` через `SupportedCurrencyService`; bootstrap вызывает `findAllSupported` и `insert` с valid `{ctx}`; у `findById`/`findAll` direct production caller нет ([service](../../backend/src/frameworks/supported-currency-service/supported-currency-service.ts#L23), [bootstrap](../../backend/src/frameworks/frameworks.layer.ts#L18)) | Да для direct methods и делегирующего path | Functional/snapshots для всех методов, только без trx ([spec](../../backend/src/frameworks/relational-data-service/postgres/repositories/__tests__/currency.repository.spec.ts#L30)); bootstrap integration покрыт отдельно ([framework test](../../backend/src/frameworks/__tests__/frameworks.layer.test.ts#L1)). |
| `EventShareToken`: `findByToken`, `findOneActiveByEventId`, `findAll`, `insert`, `deleteByToken`, `deleteExpiredBatch` ([adapter](../../backend/src/frameworks/relational-data-service/postgres/repositories/event-share-token.repository.ts#L21), [port](../../backend/src/domain/abstracts/relational-data-service/repositories/event-share-token.repository.ts#L4)) | Да, все | Шесть одинаковых checks ([adapter](../../backend/src/frameworks/relational-data-service/postgres/repositories/event-share-token.repository.ts#L21)) | Token read/create flows вызывают find/insert без trx; cleanup вызывает `deleteExpiredBatch` с valid trx; у `findAll`/`deleteByToken` production caller нет ([create](../../backend/src/usecases/users/v2/create-event-share-token-v2.usecase.ts#L24), [read](../../backend/src/usecases/users/v2/get-event-info-v2.usecase.ts#L35), [cleanup](../../backend/src/usecases/cron/cleanup-expired-data.usecase.ts#L33)) | Да для всех шести | Functional/snapshots для всех шести без trx ([spec](../../backend/src/frameworks/relational-data-service/postgres/repositories/__tests__/event-share-token.repository.spec.ts#L32)); cleanup use-case test проверяет transaction advisory lock, не invalid context ([test](../../backend/src/usecases/cron/__tests__/cleanup-expired-data.usecase.test.ts#L88)). |
| `Event`: `findById`, `findAll`, `insert`, `update` ([adapter](../../backend/src/frameworks/relational-data-service/postgres/repositories/event.repository.ts#L21), [port](../../backend/src/domain/abstracts/relational-data-service/repositories/event.repository.ts#L4)) | Да, все; lock фактически требует transaction | Четыре одинаковых checks; lock options применяются после silent fallback ([adapter](../../backend/src/frameworks/relational-data-service/postgres/repositories/event.repository.ts#L21)) | `findById` — non-transactional reads и valid transaction mutation flows; `insert` — save event через idempotency trx; `update` — delete trx; у `findAll` production caller нет ([read](../../backend/src/usecases/users/get-event-info.usecase.ts#L25), [save](../../backend/src/usecases/users/save-event.usecase.ts#L48), [delete](../../backend/src/usecases/users/delete-event.usecase.ts#L31)) | Да; особенно высокий для locked read + following write | Functional/snapshots; valid transaction/lock, competing lock failures и rollback проверены ([spec lock](../../backend/src/frameworks/relational-data-service/postgres/repositories/__tests__/event.repository.spec.ts#L81), [rollback](../../backend/src/frameworks/relational-data-service/postgres/repositories/__tests__/event.repository.spec.ts#L239)); invalid context не проверяется. |
| `Expense`: `findByEventId`, `findAll`, `insert` ([adapter](../../backend/src/frameworks/relational-data-service/postgres/repositories/expense.repository.ts#L20), [port](../../backend/src/domain/abstracts/relational-data-service/repositories/expense.repository.ts#L4)) | Да, все | Три одинаковых checks ([adapter](../../backend/src/frameworks/relational-data-service/postgres/repositories/expense.repository.ts#L20)) | Reads вызывают `findByEventId` без trx; V1/V2 mutations вызывают `insert` с valid idempotency trx; у `findAll` production caller нет ([V1 read](../../backend/src/usecases/users/get-event-expenses.usecase.ts#L21), [V2 read](../../backend/src/usecases/users/v2/get-event-expenses-v2.usecase.ts#L24), [V2 write](../../backend/src/usecases/users/v2/save-event-expense-v2.usecase.ts#L69)) | Да для всех трёх | Functional/snapshots для всех трёх без trx ([spec](../../backend/src/frameworks/relational-data-service/postgres/repositories/__tests__/expense.repository.spec.ts#L30)); DB-backed mutation use-case suites exercise valid trx but do not assert seam rejection. |
| `IdempotencyKey`: `findByKey`, `findAll`, `insert`, `delete`, `deleteExpiredBatch` ([adapter](../../backend/src/frameworks/relational-data-service/postgres/repositories/idempotency-key.repository.ts#L18), [port](../../backend/src/domain/abstracts/relational-data-service/repositories/idempotency-key.repository.ts#L6)) | Да | Пять identical fallback checks ([adapter](../../backend/src/frameworks/relational-data-service/postgres/repositories/idempotency-key.repository.ts#L18)) | `findByKey`/`insert`/`delete` — valid idempotency trx; `deleteExpiredBatch` — valid cleanup trx; у `findAll` production caller нет ([idempotency](../../backend/src/usecases/shared/idempotency.usecase.ts#L102), [cleanup](../../backend/src/usecases/cron/cleanup-expired-data.usecase.ts#L33)) | Да для пяти | Functional/snapshots без trx ([spec](../../backend/src/frameworks/relational-data-service/postgres/repositories/__tests__/idempotency-key.repository.spec.ts#L40)); idempotency DB test подтверждает, что operation получает defined ctx ([test](../../backend/src/usecases/shared/__tests__/idempotency.usecase.test.ts#L46)). |
| `IdempotencyKey.tryAcquireLock` ([adapter](../../backend/src/frameworks/relational-data-service/postgres/repositories/idempotency-key.repository.ts#L53), [port](../../backend/src/domain/abstracts/relational-data-service/repositories/idempotency-key.repository.ts#L22)) | Нет | Уже fail-fast: требует `EntityManager`, затем делает `EntityManager.query` | Только shared idempotency use case с valid trx ([caller](../../backend/src/usecases/shared/idempotency.usecase.ts#L127)) | Нет fallback; interface всё ещё допускает `{}` из-за optional `ctx` | DB-backed test проверяет transaction-scoped advisory lock с двумя настоящими managers ([spec](../../backend/src/frameworks/relational-data-service/postgres/repositories/__tests__/idempotency-key.repository.spec.ts#L57)); invalid context test отсутствует. |
| `UserInfo`: `findByEventId`, `findAll`, `insert` ([adapter](../../backend/src/frameworks/relational-data-service/postgres/repositories/user-info.repository.ts#L20), [port](../../backend/src/domain/abstracts/relational-data-service/repositories/user-info.repository.ts#L4)) | Да, все | Три одинаковых checks ([adapter](../../backend/src/frameworks/relational-data-service/postgres/repositories/user-info.repository.ts#L20)) | Reads вызывают `findByEventId` без trx; save event/add-users вызывают `insert` с valid idempotency trx; у `findAll` production caller нет ([read V1](../../backend/src/usecases/users/get-event-info.usecase.ts#L25), [read V2](../../backend/src/usecases/users/v2/get-event-info-v2.usecase.ts#L35), [write](../../backend/src/usecases/users/v2/save-users-to-event-v2.usecase.ts#L47)) | Да для всех трёх | Functional/snapshots для всех трёх без trx ([spec](../../backend/src/frameworks/relational-data-service/postgres/repositories/__tests__/user-info.repository.spec.ts#L28)); mutation use-case suites exercise valid trx but not invalid context. |

Итого: 31 method принимает transaction object: 29 содержат direct silent-fallback check, `currency.findAllSupported` делегирует в один из них и наследует риск, один (`tryAcquireLock`) уже fail-fast.

## 3. Callers с пустым или неверным context

Их не найдено.

Проверены:

- все production упоминания `.transaction(` и все создания/передачи `{ctx}` под `backend/src`;
- все production usages `trx` в use cases, framework services и repositories;
- все tests под `backend/src/**/__tests__` и `backend/test` на `{}`, `{ctx: undefined}`, `ctx: null`, primitive/fake objects, manual `ITransaction` construction и transaction mocks;
- все `instanceof EntityManager` и прямые `.query` calls.

Найденные test contexts получают настоящий manager из `dataSource.transaction`, например event lock/rollback tests ([event spec](../../backend/src/frameworks/relational-data-service/postgres/repositories/__tests__/event.repository.spec.ts#L81)) и idempotency advisory-lock test ([idempotency repository spec](../../backend/src/frameworks/relational-data-service/postgres/repositories/__tests__/idempotency-key.repository.spec.ts#L57)). Два currency-rate tests явно передают `undefined` вторым argument, что является легитимным no-transaction состоянием ([spec](../../backend/src/frameworks/relational-data-service/postgres/repositories/__tests__/currency-rate.repository.spec.ts#L196)). Единственный test-level `{}` рядом с idempotency repository является пустым delete criteria — первым argument `delete`, не transaction context ([spec](../../backend/src/frameworks/relational-data-service/postgres/repositories/__tests__/idempotency-key.repository.spec.ts#L128)).

Есть легитимное optional forwarding: `SupportedCurrencyService` передаёт `trx` variable, которое может быть `undefined`, и `FetchAndSaveCurrencyRateSharedUseCase` делает то же для `insert` ([service](../../backend/src/frameworks/supported-currency-service/supported-currency-service.ts#L23), [use case](../../backend/src/usecases/shared/fetch-and-save-currency-rate.usecase.ts#L24)). Поэтому runtime invariant должен проверять `trx === undefined`, а не количество arguments: explicit `undefined` эквивалентен отсутствию transaction; `{ctx: undefined}` — invalid.

## 4. Варианты seam

| Вариант | Нельзя молча выйти | TypeORM в domain/usecases | Interface / locality | Testability | Размер |
|---|---|---|---|---|---|
| 1. Локальный guard в каждом repository method | Да, если все 29 мест изменены | Не добавляет TypeORM | Interface не растёт, но invariant размножен по семи adapters и легко пропустить в новом method | Нужны повторные tests либо доверие review | Малый по концепции, большой и повторяющийся diff |
| 2. Общий framework resolver в существующем `BaseRepository` | Да: одна implementation различает три состояния | Domain сохраняет `unknown`; новый TypeORM код остаётся в framework. Существующие imports `EntityManager` в двух cron use cases остаются отдельным долгом | Малый interface: один protected method; высокая locality и leverage на семь adapters | Три состояния тестируются один раз; adapter integration подтверждает routing | Минимальный bounded change: type + base + семь adapters |
| 3. Transaction-scoped repositories / framework-specific branded wrapper | Самая сильная compile/runtime гарантия; context нельзя передать вручную | Transaction-scoped repository callback может убрать TypeORM из usecases; framework-branded type, наоборот, рискует протечь наружу | Более глубокий конечный module, но меняет `transaction` interface, пять roots, shared services и raw-query capabilities | Хорошая после migration, но требует значительно больше contract tests | Непропорционально велик для текущего slice; затрагивает direct-query cron paths |

### Выбор

Вариант 2. Он закрывает конкретный runtime defect в точной PostgreSQL adapter seam, не создаёт новый public/business interface и не требует преждевременного transaction-scoped service-locator refactor. Это не helper «ради устранения повторения»: его responsibility — единственная runtime policy, выбирающая global либо transaction-scoped TypeORM repository и запрещающая третье состояние.

Вариант 3 остаётся возможным будущим deepening, но сейчас смешал бы fail-safe fix с удалением TypeORM из двух cron use cases, переносом advisory-lock capabilities и перепроектированием `IRelationalDataService.transaction`. Предыдущий аудит прямо рекомендует transaction seam отдельным vertical slice, а не массовым refactor ([audit plan](backend-layering-and-repository-errors-audit.md#этап-4-углубить-repositorytransaction-modules)).

## 5. Точные invariants будущего interface

1. Repository parameter остаётся `trx?: ITransaction`; отсутствие/`undefined` — единственный способ запросить global `DataSource`.
2. Если `ITransaction` object существует, `ctx` структурно обязателен: `ITransaction { readonly ctx: unknown }`.
3. `ITransactionWithLock` расширяет `ITransaction`; нельзя выразить lock options без context. Lock strings и semantics не меняются.
4. Domain/usecase types не импортируют TypeORM. `unknown` сужается только внутри PostgreSQL adapter.
5. Framework resolver принимает `trx === undefined` и возвращает `dataSource.getRepository(entity)`.
6. Framework resolver принимает context как valid только если `trx.ctx instanceof EntityManager`, `trx.ctx.dataSource === dataSource`, `trx.ctx.queryRunner` существует, не освобождён и `isTransactionActive === true`; затем возвращает `trx.ctx.getRepository(entity)`. Проверка только `instanceof` недостаточна: global `dataSource.manager` тоже является `EntityManager`, но TypeORM требует внутри transaction использовать специальный callback manager. `queryRunner` является признаком transaction-scoped manager ([EntityManager API](https://typeorm.io/docs/working-with-entity-manager/entity-manager-api/)).
7. Любой другой non-`undefined` transaction object, включая global/foreign/inactive manager, вызывает обычный `TypeError` с детерминированным internal message до `getRepository`, query builder и SQL.
8. Invalid context не является `Result`, business error или новым error code; существующий transport catch-all остаётся единственным внешним поведением, если такая programmer error достигнет transport.
9. `tryAcquireLock` остаётся transaction-only и выполняет raw query только через validated `EntityManager`; его SQL и lock semantics не меняются.
10. Один callback manager должен проходить через все repository calls конкретной operation; resolver никогда не заменяет valid manager глобальным `DataSource`.

## 6. Минимальный bounded-дизайн

### Точная seam

Добавить в framework-only `BaseRepository` две protected operations над одной validation policy:

```ts
requireTransactionManager(dataSource, trx): EntityManager
getTypeormRepository(dataSource, entityTarget, trx?): Repository<TEntity>
```

Их interface включает три состояния и `TypeError` mode выше. `requireTransactionManager` владеет единственной validation policy: runtime class, принадлежность manager тому же `DataSource` и состояние transaction `QueryRunner`. `getTypeormRepository` выбирает global repository только при `trx === undefined`, иначе использует проверенный manager; public domain repository ports об этом не знают. Каждый concrete adapter сохраняет маленький entity-specific private `getRepository(trx?)`, который делегирует в base operation с соответствующим entity target. Все methods передают исходный `trx`, а не предварительно вычисленный `ctx`; значит invalid state не может потеряться до seam. `idempotencyKey.tryAcquireLock` вызывает `requireTransactionManager` напрямую перед raw query, поэтому transaction-only method не может получить global fallback.

Не добавлять отдельный error class, error code или standalone helper hierarchy. Не менять `RelationalDataService.transaction`, query builders, raw SQL, isolation/lock options либо return tuples в этом slice.

### Production-файлы

- `backend/src/domain/abstracts/relational-data-service/types.ts`: обязательный `ctx`, наследование `ITransactionWithLock extends ITransaction`.
- `backend/src/frameworks/relational-data-service/postgres/repositories/base.repository.ts`: единая framework validation/repository selection operation.
- Семь adapters: `currency-rate.repository.ts`, `currency.repository.ts`, `event-share-token.repository.ts`, `event.repository.ts`, `expense.repository.ts`, `idempotency-key.repository.ts`, `user-info.repository.ts`: удалить local silent-fallback expressions и передавать полный `trx` в base seam.
- В `idempotency-key.repository.ts` сохранить/переиспользовать explicit transaction-only validation для `tryAcquireLock`; raw query не переносить.

Другие production files не нужны. В частности, не трогать transports, filters, controllers, migrations, entities, query snapshots, `backend/src/main.ts` или `.playwright-mcp/`.

### Tests (план; в этой сессии не запускались)

Unit/framework test для `BaseRepository`:

- `undefined` выбирает mocked `DataSource.getRepository`;
- настоящий callback `EntityManager` того же `DataSource` с активной transaction выбирает `manager.getRepository` и не обращается к global data source;
- `{}` (compile-negative fixture через `@ts-expect-error` либо runtime cast), `{ctx: undefined}` и `{ctx: {}}` бросают `TypeError`;
- `{ctx: dataSource.manager}`, manager другого `DataSource` и manager после завершения callback также бросают `TypeError`;
- в invalid cases оба `getRepository` spies и query-builder spies не вызываются.

DB-backed adapter assertions:

- сохранить существующие event valid-transaction и rollback tests: update виден внутри transaction и исчезает после throw ([existing tests](../../backend/src/frameworks/relational-data-service/postgres/repositories/__tests__/event.repository.spec.ts#L239));
- добавить representative read и write invalid-context cases в event repository spec: Promise rejected до SQL, row неизменна;
- добавить invalid-context assertion для `tryAcquireLock`, сохранив существующую двух-manager advisory-lock проверку ([existing test](../../backend/src/frameworks/relational-data-service/postgres/repositories/__tests__/idempotency-key.repository.spec.ts#L57));
- существующие seven repository suites продолжают доказывать разрешённый no-transaction path и неизменный query shape/snapshots.

Ожидается нулевой snapshot diff: resolver меняет только выбор repository/ошибку до query, не query builders. Test commands выполняет пользователь по backend guidance: как минимум `typecheck`, targeted repository/use-case suites, затем полный backend gate.

### Transport/schema impact

Нет изменений HTTP/gRPC DTO/status/error codes, Result/exception policy, controller unwrap, SQLSTATE mapping, retry behavior, SQL/query shape, lock semantics, entities или migrations.

## 7. Риски и открытые вопросы

- `instanceof EntityManager` предполагает единственную runtime copy TypeORM. В текущем lockfile одна root dependency `typeorm: 1.1.1`; adapter уже использует тот же check во всех repositories, поэтому bounded change не вводит новую runtime assumption, а централизует существующую ([package](../../backend/package.json#L60)). Проверки `dataSource` и active `QueryRunner` не устраняют multiple-realm limitation; если позже появятся изолированные TypeORM copies/realm, branded adapter handle будет устойчивее.
- Required `ctx` — source-compatible для найденных production/test callers, но может обнаружить внеиндексный consumer при `typecheck`. Полный repo search таких callers не показал; это нужно считать compile-time gate, не повод сохранять optional field.
- Два cron use cases сейчас импортируют TypeORM `EntityManager` и вызывают `query` напрямую ([cleanup](../../backend/src/usecases/cron/cleanup-expired-data.usecase.ts#L1), [rates](../../backend/src/usecases/cron/fetch-daily-currency-rates.usecase.ts#L1)). Выбранный slice не добавляет новых TypeORM dependencies, но и не устраняет этот существующий layering debt. Перенос advisory locks в capability port — отдельное изменение и изменило бы blast radius.
- `RelationalDataService.transaction` остаётся generic и позволяет caller выбрать неверный compile-time `TEntityManager`. Runtime repository seam всё равно защитит SQL paths; полное type-safe исправление требует варианта 3.
- `idempotencyKey.delete` сначала отвергает empty criteria, затем разрешает repository. При одновременно empty criteria и invalid context первой будет criteria error, но SQL всё равно не выполняется. Если нужен единый precedence programmer errors, это отдельное решение; для атомарности оно несущественно.

## 8. Implementation Readiness

**Implementation Readiness: PASS.**

Scope стабилен: известны все семь adapters, 31 method, пять production transaction roots, три direct `EntityManager.query` paths и существующие relevant tests. Invalid/fake callers отсутствуют; optional-`undefined` callers определены. Выбранная seam локальна framework layer, verification конкретна, transport/schema impact отсутствует.

Перед изменением production-кода требуется явное подтверждение пользователя.
