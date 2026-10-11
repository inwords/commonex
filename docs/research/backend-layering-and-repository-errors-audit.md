# Аудит слоёв backend и ошибок репозиториев

Проверено: 23.09.2026. Статус реализации обновлён: 11.10.2026.

## Цель и границы

Цель аудита — проверить направление зависимостей `api -> usecases -> frameworks -> domain`, устройство интерфейсов репозиториев и путь ошибок от PostgreSQL/TypeORM до HTTP и gRPC. Это план изменений, а не выполненный рефакторинг.

Проверены production-файлы `backend/src/{api,usecases,frameworks,domain,packages}`, Nest-модули, PostgreSQL-адаптеры, связанные тесты и миграции. CodeGraph был выбран первым способом навигации из-за наличия `.codegraph/`, но CLI `codegraph` отсутствует в окружении, а MCP-инструмент CodeGraph/ast-index недоступен. После этой проверки использован точечный fallback через `rg`, `nl` и чтение конкретных файлов.

## Статус реализации

P0-срез атомарной идемпотентности реализован в рабочем дереве; 03.10.2026 пользователь подтвердил успешный прогон тестов и typecheck:

- конкурентное выполнение одного ключа защищено PostgreSQL transaction-scoped advisory lock;
- транзакцией управляет существующий `IdempotencySharedUseCase`, без дополнительной orchestration-прослойки;
- стабильный versioned operation ID и response schema version добавлены через expand/contract; legacy URL/status/hash/response временно dual-read/dual-write для совместимости rolling deployment;
- replay использует versioned JSON codec с восстановлением `Date`;
- эффективный TTL ключей сокращён до 5 минут и проверяется при lookup, независимо от сохранённого legacy expiry и ежедневного cleanup;
- hash mismatch и request-in-progress входят в `Result`; transport-слои отображают in-progress в HTTP `409` / business code `B4016` и gRPC `ABORTED`;
- добавлена миграция и тестовые сценарии; тесты и typecheck прошли в пользовательском окружении, агент повторно их не запускал.

Дополнительный статус на 11.10.2026:

- Пользователь подтвердил завершение compatibility window старой идемпотентности. Согласована двухэтапная поставка: сначала код без legacy reads/writes, затем отдельная contract migration после выкладки этого кода и остановки предыдущих инстансов.
- Первый этап реализован: `legacyOperationId`, transport URL/status, legacy hash и legacy replay удалены из API/domain/usecases. Новые записи сохраняют только versioned operation ID/hash/response/schema version. Versioned records предыдущей версии продолжают replay; codec с `Date`, advisory lock и эффективный TTL 5 минут сохранены.
- Первый этап не менял схему: старые nullable-колонки оставались только в persistence entity с `select/insert/update: false`.
- 11.10.2026 пользователь подтвердил выкладку первого этапа и остановку всех предыдущих экземпляров с dual-write; второй этап согласован.
- Второй этап подготовлен в рабочем дереве: [contract migration](../../backend/migrations/default/1791727440000-IdempotencyContract.ts) удаляет только истёкшие legacy-only записи (все четыре versioned-поля `NULL`), удаляет `url`, `request_hash`, `response`, `status_code` и устанавливает `NOT NULL` для `operation_id`, `request_hash_v2`, `response_v2`, `response_version`. Persistence entity приведена к этой схеме. Имена `request_hash_v2`/`response_v2` и все полностью заполненные versioned records сохраняются, включая просроченные.
- Критерий истечения совпадает с runtime: `LEAST(expires_at, created_at + INTERVAL '5 minutes') <= CURRENT_TIMESTAMP`. Миграция берёт `ACCESS EXCLUSIVE` lock; существующий runner выполняет каждую миграцию в transaction. Любая оставшаяся неполная запись блокирует миграцию с явной ошибкой, включая активную legacy-only и просроченную частично versioned; cleanup и DDL откатываются вместе. `down` возвращает nullable expand-схему, но не восстанавливает удалённые legacy payloads и записи. Production-применение второго этапа в этой сессии не выполнялось.

- TypeORM удалён из domain: универсальный `delete(FindOptionsWhere)` заменён на `deleteByKey(key)`; cleanup уже использует `deleteExpiredBatch({expiresBefore, limit})`, поэтому отдельный `deleteExpiredBefore` не нужен.
- Domain business errors наследуются от `Error`, не содержат `HttpStatus`/`httpCode`; HTTP mapping находится в business-error filter.
- Health use case возвращает `Promise<void>` без transport-типа.
- Transaction handle разрешается через framework-internal registry; некорректные, чужие и завершённые handles отклоняются, а не отключают транзакцию.
- По решению пользователя от 11.10.2026 wiring остаётся в `UseCasesModule`; отдельный composition-модуль не вводится.
- HTTP V1/V2/V3 и gRPC controllers используют общий API helper `unwrapResult`: успешное значение возвращается без изменений, исходная business error бросается для существующих transport filters.
- По решению пользователя от 11.10.2026 `initialize`/`destroy` остаются в domain data interface: use cases их не вызывают, а отдельное сужение этих двух методов не оправдывает изменения wiring. Удаление lifecycle-методов исключено из плана.
- Удаление `queryDetails` остаётся несогласованным предложением; реализация не начата.
- Share-token invariant подтверждён пользователем и реализован: не более одной записи на событие, unique index по `eventId`, создание/переиспользование в `READ COMMITTED` transaction с блокировкой события. Истёкшая запись заменяется; TTL остаётся 14 дней.
- Миграция share tokens сохраняет максимальный `expiresAt`; при равенстве — максимальный `createdAt`, затем минимальный `token`. Остальные записи удаляются, их ссылки перестают работать. `down` удаляет индекс, но не восстанавливает удалённые записи. При rolling deployment старые инстансы могут получить unique conflict до их drain; новые инстансы сериализуют создание и возвращают один token.

Проверка второго этапа в этой сессии: `db:migrate` применён к локальной тестовой БД; 295 unit/DB тестов, 51 SQL snapshot и 63 E2E (включая 7 новых migration-сценариев) прошли последовательно. `typecheck`, `lint:check`, `format:check`, `build` прошли. Read-only `typeorm schema:log` подтвердил отсутствие расхождений entity metadata и применённой схемы. [Тесты миграции](../../backend/test/idempotency-contract-migration.e2e-spec.ts) проверяют сохранность payload/Date codec, имена и nullability колонок, блокировку неполных записей, atomic rollback и восстановление expand-схемы через `down`.

Read-only preflight перед production-применением второго этапа (выполнять с `search_path` целевой схемы до удаления legacy-колонок):

```sql
WITH records AS (
  SELECT
    operation_id IS NULL AND request_hash_v2 IS NULL
      AND response_v2 IS NULL AND response_version IS NULL AS legacy_only,
    operation_id IS NOT NULL AND request_hash_v2 IS NOT NULL
      AND response_v2 IS NOT NULL AND response_version IS NOT NULL AS complete,
    LEAST(expires_at, created_at + INTERVAL '5 minutes') <= CURRENT_TIMESTAMP AS expired
  FROM idempotency_keys
)
SELECT
  COUNT(*) FILTER (WHERE legacy_only AND expired) AS expired_legacy_only,
  COUNT(*) FILTER (WHERE complete AND NOT expired) AS live_versioned,
  COUNT(*) FILTER (WHERE NOT complete AND NOT (legacy_only AND expired)) AS blocking_incomplete
FROM records;
```

`blocking_incomplete` должен быть равен нулю. Preflight не заменяет проверку внутри transaction миграции; production-запрос в этой сессии не выполнялся.

Остальные пункты остаются планом. Исходные формулировки находок ниже сохранены как снимок состояния на дату аудита; текущий статус выше имеет приоритет.

## Сначала уточнить правило зависимостей

Линейная запись полезна для **runtime-потока**:

```text
HTTP/gRPC -> use case -> repository port -> PostgreSQL adapter -> database
```

Но source-level зависимости при dependency inversion не должны быть той же линейной стрелкой. Рекомендуемая модель:

```text
api        ──> usecases, frameworks, domain
usecases   ──> frameworks, domain
frameworks ──> domain
domain     ──> свои разделы, чистые packages
```

- `domain` не импортирует Nest, TypeORM, API, usecases или frameworks;
- По уточнённому решению пользователя `usecases` может импортировать frameworks/domain и внешние зависимости; запрещён импорт API.
- `frameworks` реализует порты, определённые внутренним слоем, поэтому импорт `frameworks -> domain` корректен;
- `api` переводит transport DTO/status/metadata в интерфейс use case;
- composition root связывает реализации с портами. Он может знать обо всех слоях, но не должен заставлять бизнес-код знать конкретный адаптер.

Сейчас wiring размещён в [`UseCasesModule`](../../backend/src/usecases/usecases.layer.ts#L1), который импортирует `FrameworksLayer`. Это соответствует буквальной цепочке пользователя, но смешивает бизнес-слой и composition root. По решению пользователя от 11.10.2026 текущий wiring сохраняется; перенос в отдельный composition-модуль исключён из плана. При этом сами use case-классы уже зависят в основном от domain-портов, а PostgreSQL-реализации корректно зависят от их интерфейсов.

## Резюме находок

| Приоритет | Статус | Изменение | Основной риск сейчас |
| --- | --- | --- | --- |
| P0 | ✅ Реализовано в рабочем дереве; tests и typecheck подтверждены пользователем | Сделать идемпотентность атомарной | Mixed-version rollout не даёт полной атомарности до drain старых инстансов |
| P1 | ✅ Код очищен; contract migration подготовлена и проверена локально | Убрать transport data из идемпотентности | Production-применение второго этапа ещё не подтверждено |
| P1 | ✅ Реализовано | Убрать TypeORM из domain/usecases | Удаление по ключу и пакетный cleanup имеют предметные интерфейсы |
| P1 | ✅ Проверенные business outcomes отражены в `Output`; unknown errors получают `INTERNAL_ERROR` | Ввести единый контракт ожидаемых и неожиданных ошибок | Контракт проверен для 10 business errors; новые outcomes требуют отдельного решения |
| P1 | ✅ Реализовано | Убрать HTTP status из domain | HTTP mapping находится в transport filter |
| P1 | ✅ Scoped ESLint gate добавлен по согласованной модели | Автоматически проверять импорты между слоями | Статические импорты защищены; dynamic import/require не входят в этот gate |
| P2 | ✅ Реализован fail-fast handle | Сделать транзакционный seam fail-safe | Неверный, чужой или завершённый handle отклоняется |
| P2 | Подтверждённая проблема интерфейса | Убрать `queryDetails` из domain-портов | Production-интерфейс каждого репозитория расширен только ради adapter-тестов |
| P2 | Отменено пользователем 11.10.2026 | Разделить общий `RelationalDataServiceAbstract` на capability ports | Текущий общий порт сохраняется; дальнейшее сужение не планируется |
| P3 | ✅ Реализовано для активных business errors | Унифицировать классы ошибок и barrel exports | Классы наследуют `Error`, barrel экспортирует активные типы |
| P3 | Вариант дизайна | Переместить чистые event rules из frameworks | Единственная реализация абстракции создаёт неглубокий, гипотетический seam |
| P3 | Вариант дизайна | Развязать gRPC DTO от HTTP DTO | Два transport-адаптера меняются совместно |
| P3 | ✅ Инвариант подтверждён и реализован | Атомарно переиспользовать один активный share token | Unique index и транзакционная блокировка предотвращают дубликаты |

## Подтверждённые проблемы

### 1. TypeORM протёк в domain и usecases

Domain-интерфейс `IdempotencyKeyRepositoryAbstract.delete` принимает `FindOptionsWhere` из TypeORM, а use case строит `LessThan(new Date())` из того же ORM: [`idempotency-key.repository.ts`](../../backend/src/domain/abstracts/relational-data-service/repositories/idempotency-key.repository.ts#L1), [`cleanup-idempotency-keys.usecase.ts`](../../backend/src/usecases/cron/cleanup-idempotency-keys.usecase.ts#L1).

Это прямое нарушение заявленного правила: нижний `domain` знает инфраструктурную библиотеку, а application logic формирует persistence-критерий.

Предлагаемый интерфейс — предметная операция `deleteExpiredBefore(cutoff: Date): Promise<void>`. Только PostgreSQL-адаптер переводит её в `LessThan`, SQL или query builder. Не следует заменять TypeORM другим универсальным DSL: такой интерфейс останется неглубоким и снова раскроет реализацию вызывающему коду.

### 2. Domain-ошибки содержат HTTP-представление

Все бизнес-ошибки импортируют Nest `HttpStatus` и содержат `httpCode`: [`errors.ts`](../../backend/src/domain/errors/errors.ts#L1). При этом gRPC уже имеет отдельную карту `ErrorCode -> status`: [`grpc-status.map.ts`](../../backend/src/api/grpc/filters/grpc-status.map.ts#L1).

Domain должен владеть стабильным business code, именем и безопасным сообщением. HTTP status должен определяться в HTTP filter рядом с gRPC mapping. Это позволит менять transport-семантику без изменения domain и устранит импорт `@nestjs/common` из самого нижнего слоя.

Отдельный transport leak находится в health use case: его output — `HealthIndicatorResult` из `@nestjs/terminus`: [`health-check.usecase.ts`](../../backend/src/usecases/health/health-check.usecase.ts#L1). Use case достаточно вернуть transport-neutral health state либо просто успешно завершиться; Nest-форму должен собрать controller/indicator adapter.

### 3. Модель ошибок смешивает `Result` и исключения

Проверка текущего кода от 11.10.2026: для всех user-facing use cases V1/V2/V3 возвращаемые business errors, включая ошибки `EventService` и `IdempotencySharedUseCase`, входят в `Output`. Пропущенных ожидаемых ошибок не найдено; прежняя формулировка резюме о неполных `Output` устарела. Controllers используют общий `unwrapResult`. Оставшиеся exceptions при fetch failure, некорректной форме replay или неподдерживаемой версии codec относятся к сбоям инфраструктуры/инвариантов, а не к новым business outcomes. Исходное описание ниже сохранено как историческая находка.

Большинство use cases объявляет `Result<Value, BusinessError>` и возвращает ожидаемые отказы как значение. Затем HTTP/gRPC controllers повторяют `if (isError(result)) throw result.error`, например [`user.controller.ts`](../../backend/src/api/http/user/user.controller.ts#L48).

Текущее состояние:

- PostgreSQL `55P03` больше не преобразуется в `EventOperationConflictError`: event repository сохраняет `QueryFailedError`, а HTTP/gRPC catch-all возвращают `INTERNAL_ERROR / B4007` ([repository](../../backend/src/frameworks/relational-data-service/postgres/repositories/event.repository.ts#L21), [HTTP filter](../../backend/src/api/http/filters/internal-error.filter.ts#L16), [gRPC filter](../../backend/src/api/grpc/filters/grpc-internal-error.filter.ts#L25));
- `IdempotencySharedUseCase` возвращает `IdempotencyHashMismatchError` и `IdempotencyRequestInProgressError` через `Result`, а затронутые mutation use cases включают `IdempotencyError` в `Output` ([idempotency use case](../../backend/src/usecases/shared/idempotency.usecase.ts#L95), [expense output](../../backend/src/usecases/users/save-event-expense.usecase.ts#L29)).

В типах use cases по-прежнему не отражаются неожиданные infrastructure failures; это намеренно, поскольку они обрабатываются transport catch-all. Обоснование границы зафиксировано в [primary-source notes](postgresql-typeorm-expected-errors-primary-sources.md#exact-propagation-and-retry-boundary).

Рекомендуемая политика:

1. Ожидаемый бизнес-отказ, после которого клиент может изменить запрос или повторить его осознанно, входит в `Result` use case.
2. Адаптер переводит только распознанные vendor errors в нейтральные ошибки порта, например `ResourceBusy` или `IdempotencyKeyConflict`; use case переводит их в business outcome.
3. Неожиданные инфраструктурные сбои остаются исключениями, логируются один раз на transport boundary и получают единый безопасный `INTERNAL_ERROR` без SQL/driver details.
4. Controllers используют один helper/interceptor для распаковки `Result`, а HTTP/gRPC filters отвечают только за transport mapping.

Альтернатива — бросать типизированные application exceptions для всех ожидаемых отказов. Она тоже может быть последовательной, но смешивать оба подхода в одном интерфейсе не следует.

### 4. Неожиданные repository errors не имеют стабильного transport-контракта

Текущий статус: HTTP/gRPC catch-all filters реализованы; неизвестные ошибки и PostgreSQL lock failures получают безопасный `INTERNAL_ERROR / B4007`, подробности логируются внутри процесса. Исходное описание ниже относится к дате аудита.

Кроме `55P03` при `nowait`, write-методы выполняют `query.execute()` без перевода ошибок, например [`event.repository.ts`](../../backend/src/frameworks/relational-data-service/postgres/repositories/event.repository.ts#L79) и [`idempotency-key.repository.ts`](../../backend/src/frameworks/relational-data-service/postgres/repositories/idempotency-key.repository.ts#L42). Тест event repository явно закрепляет raw `QueryFailedError` для другого lock timeout: [`event.repository.spec.ts`](../../backend/src/frameworks/relational-data-service/postgres/repositories/__tests__/event.repository.spec.ts#L158).

HTTP глобально регистрирует только validation/business filters: [`app.factory.ts`](../../backend/src/app.factory.ts#L12). gRPC controller также регистрирует только два этих фильтра: [`user.controller.ts`](../../backend/src/api/grpc/user/user.controller.ts#L55). `ErrorCode.INTERNAL_ERROR` объявлен, но HTTP mapping отсутствует, а gRPC mapping не создаёт такую ошибку сам: [`error-codes.enum.ts`](../../backend/src/domain/errors/error-codes.enum.ts#L13), [`grpc-status.map.ts`](../../backend/src/api/grpc/filters/grpc-status.map.ts#L13).

Не нужно превращать каждую DB-ошибку в business error. Нужно:

- перечислить ожидаемые vendor cases по операциям: lock unavailable, idempotency unique conflict и, при необходимости, FK/unique conflicts;
- переводить только их рядом с адаптером;
- добавить последний HTTP/gRPC filter для неизвестной ошибки, который пишет structured log/correlation id и возвращает стабильный внешний контракт;
- проверить, что raw SQL, parameters, driver message и stack не уходят клиенту.

### 5. Идемпотентность не атомарна

Текущий порядок: `findByKey -> fn() -> insert`: [`idempotency.usecase.ts`](../../backend/src/usecases/shared/idempotency.usecase.ts#L35). Ключ является primary key в persistence entity: [`idempotency-key.entity.ts`](../../backend/src/frameworks/relational-data-service/postgres/entities/idempotency-key.entity.ts#L5).

Из последовательности операций и ограничения primary key следует риск: при двух одновременных запросах с новым одинаковым ключом оба могут не увидеть запись, оба выполнить side effect, затем один insert завершится `23505`. Конкурентной репродукции в рамках аудита не было, но текущая реализация не содержит механизма, который исключал бы такой interleaving, поэтому защита не гарантирует «операция выполнена один раз» в наиболее важном сценарии.

Рекомендуемый глубокий модуль — один idempotency interface, скрывающий reservation, hash comparison, in-progress state и сохранение ответа. Возможные реализации:

- атомарная reservation запись через `INSERT ... ON CONFLICT`, затем владелец выполняет операцию;
- transaction/advisory lock по key;
- state machine `in_progress/completed` с явной политикой восстановления после падения.

Выбор зависит от требуемого поведения для конкурентного повтора: ждать первый запрос, вернуть conflict/retryable response или читать завершённый результат. Это решение нужно записать до реализации. Обязательная проверка — два реально параллельных DB-backed запроса, а не последовательный replay test.

Текущие unit/DB-backed проверки покрывают новый ключ, последовательный replay и hash mismatch, но не запускают два `execute` конкурентно: [`idempotency.usecase.test.ts`](../../backend/src/usecases/shared/__tests__/idempotency.usecase.test.ts#L69). HTTP E2E также отправляет повторы последовательно. Поэтому существующие зелёные тесты не опровергают гонку.

### 5.1. Idempotency interface содержит transport data и небезопасно восстанавливает типы

Текущий статус: transport data и legacy fallback удалены из рабочего кода. Стабильный operation ID и versioned codec сохраняют форму успешного replay. Legacy-колонки удалены из persistence metadata; отдельная contract migration подготовлена и проверена локально. Исходное описание проблемы ниже относится к состоянию на дату аудита.

`IdempotentInput` требует `url`, HTTP передаёт `request.url`, а gRPC вынужден придумывать строки вида `grpc:CreateEvent`: [`idempotency.usecase.ts`](../../backend/src/usecases/shared/idempotency.usecase.ts#L11), [`user.controller.ts`](../../backend/src/api/grpc/user/user.controller.ts#L73). Domain entity также хранит `statusCode`, хотя use case всегда записывает HTTP-подобное значение `200`: [`idempotency-key.entity.ts`](../../backend/src/domain/entities/idempotency-key.entity.ts#L1), [`idempotency.usecase.ts`](../../backend/src/usecases/shared/idempotency.usecase.ts#L53). Это transport concerns внутри use case/domain interface.

Дополнительно response сохраняется как `jsonb`, а replay делает unchecked cast `existing.response as TResult`. JSON round-trip не сохраняет `Date`: текущий gRPC adapter уже содержит специальный mapper, который принимает `Date | string` именно из-за replay: [`iso-date.ts`](../../backend/src/api/grpc/user/dto/iso-date.ts#L1). Следовательно, один и тот же use case фактически возвращает разные runtime shapes при первом выполнении и replay, хотя TypeScript обещает один `TResult`.

Idempotency module должен получать стабильный operation key, не transport URL, и хранить versioned serialized response либо transport-neutral result с явным codec. HTTP status выбирается API-слоем после replay так же, как после первого выполнения. Codec должен гарантировать одинаковую runtime shape или честно возвращать serialized DTO-type.

### 6. Transaction seam может молча вывести запрос из транзакции

Domain задаёт `ctx?: unknown` и одновременно раскрывает TypeORM/PostgreSQL-подобные lock strings `pessimistic_read`, `pessimistic_write`, `nowait`, `skip_locked`: [`types.ts`](../../backend/src/domain/abstracts/relational-data-service/types.ts#L1). Каждый PostgreSQL repository проверяет `ctx instanceof EntityManager`, а при несовпадении использует обычный `DataSource`, например [`event.repository.ts`](../../backend/src/frameworks/relational-data-service/postgres/repositories/event.repository.ts#L24). Ошибка wiring или неверный context поэтому не падает, а тихо выполняет запрос вне ожидаемой транзакции. Даже без прямого импорта TypeORM интерфейс уже воспроизводит vocabulary конкретного persistence adapter.

Предпочтительный интерфейс:

```ts
transaction((repositories) => repositories.event.findById(...))
```

Callback получает transaction-scoped набор портов, поэтому context не прокидывается в каждый метод. Менее глубокий промежуточный вариант — opaque branded handle, который адаптер обязан валидировать с fail-fast exception. Generic isolation level в [`types.ts`](../../backend/src/domain/abstracts/relational-data-service/types.ts#L16) также следует заменить явным transport-neutral union только поддерживаемых уровней.

### 7. `queryDetails` расширяет каждый domain-порт ради adapter-тестов

Все repository methods возвращают tuple `[result, queryDetails]`, например [`event.repository.ts`](../../backend/src/domain/abstracts/relational-data-service/repositories/event.repository.ts#L4), а `IQueryDetails` хранит query string/parameters: [`types.ts`](../../backend/src/domain/abstracts/relational-data-service/types.ts#L11). Production callers берут только первый tuple item; SQL details нужны repository snapshot tests.

Это shallow interface: знание о диагностике SQL размножено по каждому методу и каждому caller, хотя поведения бизнес-коду не добавляет. Production port должен возвращать только результат. SQL snapshots можно сохранить через adapter-internal observer/recorder, test-only factory option или прямой helper вокруг query builder. Test seam не должен становиться частью domain interface.

### 8. `RelationalDataServiceAbstract` — service locator с lifecycle-методами

Один объект раскрывает семь репозиториев, `initialize`, `destroy`, `healthCheck` и транзакции: [`relational-data-service.ts`](../../backend/src/domain/abstracts/relational-data-service/relational-data-service.ts#L10). Даже use case, которому нужны две операции, зависит от всего интерфейса. Lifecycle database connection при этом является framework/composition concern, а не domain capability.

План углубления seam:

- Выделение capability ports отменено пользователем 11.10.2026; текущий `RelationalDataServiceAbstract` сохраняется.
- `initialize`/`destroy` остаются в текущем domain data interface по решению пользователя от 11.10.2026; отдельный lifecycle refactor не планируется;
- health получает отдельный `HealthProbe` port;
- не создавать по интерфейсу на каждый метод: интерфейс должен скрывать сложность операции и иметь реальную заменяемость либо отчётливую test seam.

### 9. Границы слоёв не проверяются автоматически

Текущий статус: [ESLint config](../../backend/eslint.config.js) содержит production-scoped `no-restricted-imports` для принятой модели зависимостей:

- `src/domain/**`: запрет `#api`, `#usecases`, `#frameworks`, Nest, TypeORM (включая subpaths), gRPC, Fastify/Express и transport validation/serialization packages;
- `src/usecases/**`: запрет только API; frameworks/domain, Nest, TypeORM и внешние зависимости разрешены. `UseCasesModule` подчиняется тем же правилам, отдельное wiring-исключение не нужно;
- `src/frameworks/**`: запрет API/usecases, разрешены domain ports и собственные infrastructure dependencies;
- `src/api/**`: импорты всех production-слоёв разрешены;
- внутри каждого слоя импорты разрешены. Domain может импортировать собственные entities/value objects/abstracts/errors и чистые `#packages`; запрет между его разделами не вводится.
- существующий production-запрет `test-support` сохранён во всех scoped configs, включая wiring;
- alias и относительные пути проверяются одинаково; production imports с `..` внутри repo-alias запрещены, чтобы `#domain/../api/...` не обходил границы; type-only imports и reexports также запрещаются. Внешний пакет `@opentelemetry/api` не считается repo-слоем `api`;
- `*.test.ts`, `*.spec.ts`, `__tests__`, `src/test-support` и `test/` освобождены от production restrictions. Gate проверяет статические imports/reexports; dynamic import и CommonJS require не входят в этот срез.

[Regression suite](../../backend/src/__tests__/layer-imports.spec.ts) читает эффективный ESLint config для synthetic file paths и проверяет запрещённые и разрешённые imports; fixtures не создаются на диске, type-aware parser service отключён только внутри fixture linter. Parser/config errors не могут пройти как разрешённый import.

Проверки gate: 58 regression-кейсов (разрешённые/запрещённые imports) прошли; полный прогон — 363 unit/DB теста, 51 SQL snapshot и 70 E2E. DB suites и E2E запускались последовательно. `typecheck`, `lint:check`, `format:check`, `build`, `git diff --check` прошли. Production-файлы не менялись.

Решение от 03.10.2026 откладывало ESLint-ограничения до завершения рефакторинга. 11.10.2026 пользователь согласовал включение gate сейчас, затем уточнил модель: API импортирует все слои; usecases — все, кроме API; frameworks — все, кроме API/usecases; domain чист от внешних слоёв и инфраструктуры, внутренние импорты разрешены. Прежние узкие DI/wiring-исключения сняты; дальнейшие структурные предложения не блокируют gate.

### 10. Публичный интерфей error-модуля синхронизирован

Barrel [`domain/errors/index.ts`](../../backend/src/domain/errors/index.ts#L1) экспортирует активные business-error типы, включая idempotency errors. Удалённый `EventOperationConflictError` больше не входит в публичный interface и `BUSINESS_ERROR_CLASSES`. Остающиеся прямые импорты `#domain/errors/errors` — вопрос единообразия, а не неполноты текущего barrel.

## Текущая error matrix

Матрица проверена по [business errors](../../backend/src/domain/errors/errors.ts), [HTTP filter](../../backend/src/api/http/filters/business-error.filter.ts) и [gRPC mapping](../../backend/src/api/grpc/filters/grpc-status.map.ts). В HTTP business code находится в JSON `code`, в gRPC — в metadata `error-code`. Указаны существующие outcomes; новые отказы для FK/unique conflicts не вводятся.

| Outcome | Где возвращается | Code | HTTP | gRPC |
| --- | --- | --- | --- | --- |
| EventNotFound | Чтение/изменение события, пользователи, расходы, share token | B4001 | 404 | NOT_FOUND |
| EventDeleted | Те же операции с удалённым событием | B4002 | 410 | FAILED_PRECONDITION |
| InvalidPinCode | PIN-защищённые чтения и изменения | B4003 | 403 | PERMISSION_DENIED |
| CurrencyNotFound | Создание события; расходы с автоматическим обменом | B4004 | 404 | NOT_FOUND |
| CurrencyRateNotFound | Расходы с автоматическим обменом; currencies V3 | B4005 | 404 | NOT_FOUND |
| InvalidToken | GetEventInfo V2: неизвестный token или token другого события | B4008 | 401 | UNAUTHENTICATED |
| TokenExpired | GetEventInfo V2: просроченный token | B4009 | 401 | UNAUTHENTICATED |
| InconsistentExchangedAmount | Расход V2: частично заполненные custom exchanged amounts | B4010 | 400 | INVALID_ARGUMENT |
| IdempotencyHashMismatch | Все пять idempotent mutation paths V1/V2 | B4011 | 422 | FAILED_PRECONDITION |
| IdempotencyRequestInProgress | Те же mutation paths при занятом advisory lock | B4016 | 409 | ABORTED |
| Validation failure | Transport DTO validation, вне `Output` use case | B4006 | 400 | INVALID_ARGUMENT |
| Unexpected infrastructure/invariant failure | Exceptions, включая lock unavailable/timeout | B4007 | 500 | INTERNAL |

Покрытие контрактов: [HTTP business filter tests](../../backend/src/api/http/filters/__tests__/business-error.filter.spec.ts) и [gRPC business filter tests](../../backend/src/api/grpc/filters/__tests__/grpc-business-error.filter.spec.ts) проверяют все 10 business errors с явно заданными status/code/message; gRPC также проверяет metadata `error-code`. HTTP и gRPC E2E покрывают все 10 business errors. Последний gRPC срез добавляет InvalidToken через GetEventInfoV2, CurrencyNotFound через CreateEvent, CurrencyRateNotFound и InconsistentExchangedAmount через CreateExpenseV2; все сценарии используют реальную БД и проверяют status, details и metadata business code. [HTTP V2](../../backend/test/http/user-v2.e2e-spec.ts) и [gRPC](../../backend/test/grpc/user.e2e-spec.ts) проверяют expiry token на реальной БД; gRPC mismatch дополнительно проверяет отсутствие второго события.

Проверки этого среза: 305 unit/DB тестов, 51 SQL snapshot и 70 E2E прошли; DB suites и E2E запускались последовательно. `typecheck`, `lint:check`, `format:check`, `build` и `git diff --check` прошли. Изменены только тесты и этот аудит; production behavior сохранено.

Retry semantics: для in-progress допустим повтор после завершения текущего запроса; для mismatch нельзя повторно использовать тот же ключ с изменённым body/operation. Остальные business outcomes требуют исправления запроса, доступа или изменения состояния; автоматический retry contract для них не добавляется. `INTERNAL_ERROR` не гарантирует retry safety, поэтому клиент не должен считать любую неизвестную ошибку основанием повторить mutation без её существующих гарантий идемпотентности.

## Варианты дизайна и места, требующие решения

### Чистые event rules находятся в frameworks

`EventService` проверяет существование, soft delete и PIN, не использует инфраструктуру и имеет одну реализацию: [`event-service.ts`](../../backend/src/frameworks/event-service/event-service.ts#L9). Абстракция в domain плюс единственный framework adapter образуют гипотетический seam. Предпочтительно переместить реализацию правил в domain как обычный глубокий модуль/функции и внедрять только то, что реально меняется или имеет внешний side effect.

### Supported-currency policy отделён от DB orchestration

По согласованному решению пользователя от 11.10.2026 `SupportedCurrencyService` остаётся в frameworks как чистый сервис: проверяет поддержку валюты, фильтрует переданные валюты и курсы и предоставляет поддерживаемые коды. Зависимость от data service и асинхронные repository-обёртки удалены.

Пять usecases самостоятельно читают currency/currency-rate repository ports, сохраняя transaction context и прежние business errors. Полный V3-ответ получает все валюты в порядке code и фильтрует их чистым сервисом; курсы также фильтруются без изменения входных данных. V3 version usecase передаёт выбранные сервисом коды в общий `findCurrenciesWithRatesVersionByDate(date, codes, trx)`: лёгкий запрос возвращает только timestamps, без загрузки rate JSON. Форма validator и порядок timestamps для `200`/`304` сохранены.

Из repositories удалены `findSupportedById`, `findAllSupported` и helper с hardcoded policy. Вместо них используются `findById`/`findAll` и явные параметры фильтра. PostgreSQL-repositories остаются в frameworks; перенос EventService, lifecycle и bootstrap composition в этот срез не входит. SQL snapshots обновляются только для удалённых запросов и нового параметризованного запроса метаданных.

Проверки среза: 366 unit/DB тестов, 50 SQL snapshots и 70 E2E прошли; DB suites и E2E запускались последовательно. `typecheck`, `lint:check`, `format:check`, `build` и `git diff --check` прошли. Чистый сервис отдельно проверен на поддержку/missing currency, порядок отбора, пустые данные и отсутствие мутации rates.

### gRPC зависит от HTTP DTO

gRPC controller и request DTO импортируют HTTP DTO, например [`user.controller.ts`](../../backend/src/api/grpc/user/user.controller.ts#L21) и [`add-users-to-event-grpc-request.dto.ts`](../../backend/src/api/grpc/user/dto/add-users-to-event-grpc-request.dto.ts#L1). Это не нарушение четырёх верхнеуровневых слоёв, но связывает два transport adapters. Общие transport-neutral input/output types могут жить у use case; HTTP и gRPC должны иметь свои DTO и mapper, даже если поля пока совпадают.

### Один активный share token

По решению пользователя от 11.10.2026 событие хранит не более одной записи share token. [Use case](../../backend/src/usecases/users/v2/create-event-share-token-v2.usecase.ts) блокирует событие в `READ COMMITTED` transaction, проверяет доступ и читает token через `findByEventId`. Активный token возвращается без изменения expiry; истёкшая запись удаляется и заменяется в той же transaction. [Persistence entity](../../backend/src/frameworks/relational-data-service/postgres/entities/event-share-token.entity.ts) содержит unique index по `eventId`.

[Миграция](../../backend/migrations/default/1791726271060-init.ts) блокирует таблицу, сохраняет запись с самым поздним expiry на событие и создаёт unique index. Удалённые дубликаты не восстанавливаются при rollback migration. DB-backed тесты воспроизводят исходную гонку и проверяют параллельное переиспользование и замену истёкшего token; HTTP/gRPC E2E проверяют единый token для конкурентных запросов обоих transport adapters.

### Versioned use cases расходятся по гарантиям

Например, V2 add-users выполняет locked read и insert в transaction: [`save-users-to-event-v2.usecase.ts`](../../backend/src/usecases/users/v2/save-users-to-event-v2.usecase.ts#L35). V1 проверяет event и вставляет users без transaction: [`save-users-to-event.usecase.ts`](../../backend/src/usecases/users/save-users-to-event.usecase.ts#L35). V1 остаётся legacy surface, а V2 — canonical по [`docs/domain.md`](../domain.md#event-access-and-lifecycle), поэтому это не нужно автоматически «исправлять» одинаковым кодом. Сначала определить срок жизни V1 и требуемую совместимость; затем либо удалить/заморозить legacy path, либо вынести общий core с явными policy differences.

## Поэтапный план

### Этап 0. Зафиксировать архитектурное решение

1. Принять source dependency model из начала документа и отличать её от runtime call flow.
2. Решить error policy: рекомендуется `Result` для ожидаемых business outcomes и exceptions только для unexpected infrastructure failures.
3. Решить concurrent replay semantics для idempotency и инвариант share token.
4. Записать короткий ADR, потому что решения затронут API, use cases, framework adapters и тестовую стратегию.

Готовность этапа: есть таблица `operation -> expected failures -> Result/error code -> HTTP status -> gRPC status -> retryability`.

### Этап 1. Закрыть гонку идемпотентности (P0)

1. Спроектировать один idempotency module interface, скрывающий reservation/replay/hash mismatch.
2. ✅ Transport URL удалён из API/usecases; стабильный operation key и versioned response codec сохраняют runtime shape.
3. ✅ HTTP `statusCode` удалён из domain record; transport определяет статус по результату операции. Отдельная contract migration подготовлена после подтверждения выкладки первого этапа и drain старых экземпляров.
4. Реализовать атомарность в PostgreSQL adapter и перевод `23505` без утечки TypeORM.
5. Сохранить успешный response в той же согласованной lifecycle-модели; определить recovery для `in_progress` после падения процесса.
6. Добавить DB-backed concurrency tests для одинакового и различного hash.
7. Добавить HTTP и gRPC tests на replay, mismatch, одинаковую форму дат и concurrent request.

Критерий готовности: side effect в параллельном тесте выполняется ровно один раз; клиент никогда не видит raw `23505`.

### Этап 2. Очистить dependency rule (P1)

1. ✅ Заменить `delete(FindOptionsWhere)` на `deleteByKey(key)`; для cleanup сохранить существующий `deleteExpiredBatch({expiresBefore, limit})`.
2. ✅ Убрать `LessThan` из use case и TypeORM из domain.
3. ✅ Убрать `HttpStatus`/`httpCode` из domain errors; добавить полную HTTP mapping рядом с gRPC mapping.
4. ✅ Сделать health output transport-neutral.
5. Исключено по решению пользователя от 11.10.2026: сохранить wiring в `UseCasesModule`.
6. ✅ Scoped ESLint restrictions и regression fixtures включены сейчас по решению от 11.10.2026; правила приведены к уточнённой модели пользователя, отдельные DI/wiring-исключения не нужны.

Критерий готовности: `domain` не импортирует Nest/TypeORM; `usecases` не импортирует API, `frameworks` не импортирует API/usecases; CI защищает уточнённую пользователем модель.

### Этап 3. Унифицировать ошибки (P1)

1. ✅ `BusinessError` объединяет активные transport-neutral классы; публичный export полный.
2. Сохранить lock conflicts и lock timeouts как infrastructure failures; не добавлять отдельную клиентскую реакцию без нового доказанного business contract.
3. ✅ Idempotency mismatch и in-progress возвращаются через `Result` и включены в mutation `Output`.
4. ✅ HTTP/gRPC catch-all filters возвращают безопасный `INTERNAL_ERROR` и логируют неизвестную ошибку; текущее observability поведение сохраняется.
5. ✅ Убрать повторяющийся `if (isError) throw` из HTTP V1/V2/V3 и gRPC controllers через общий API helper `unwrapResult`.
6. ✅ Текущая error matrix записана; HTTP/gRPC filter tests покрывают все 10 business errors. HTTP/gRPC E2E покрывают все 10 business errors; детали перечислены рядом с матрицей.

Критерий готовности: каждый ожидаемый отказ виден в типе use case; неизвестная DB-ошибка имеет одинаковый безопасный внешний контракт в HTTP и gRPC.

### Этап 4. Углубить repository/transaction modules (P2)

1. ✅ Реализован fail-fast context через framework-internal registry transaction handles.
2. Исключено по решению пользователя от 11.10.2026: сохранить `initialize`/`destroy` в domain data interface. Текущие use cases их не вызывают; польза отдельной правки не оправдывает изменения wiring.
3. Исключено по решению пользователя от 11.10.2026: capability ports не выделяются, общий `RelationalDataServiceAbstract` сохраняется.
4. Убрать `queryDetails` из production interfaces, сохранив SQL snapshot tests через framework-internal hook.
5. После каждого slice запускать repository, use case и E2E tests затронутого потока.

Критерий готовности: запрос невозможно случайно выполнить вне объявленной transaction; production caller не знает о SQL diagnostics и не получает полный service locator без необходимости.

### Этап 5. Локальные улучшения структуры (P3)

1. Переместить чистые event rules из frameworks после удаления гипотетической абстракции.
2. ✅ Supported-currency policy оставлен чистым сервисом в frameworks; DB orchestration перенесён в usecases, repositories принимают явные фильтры.
3. Разделить gRPC и HTTP DTO, оставив общим use case contract.
4. ✅ Зафиксирован строгий share-token invariant; добавлены unique index, transaction и concurrency tests.
5. Консолидировать V1/V2 core только после решения о legacy compatibility.

## Стратегия поставки и проверки

Не делать один массовый refactor. Безопасный порядок PR:

1. characterization/concurrency tests;
2. idempotency fix;
3. ORM leak cleanup и import guards;
4. error matrix и transport filters;
5. transaction seam одним vertical slice;
6. постепенное сужение ports и удаление query diagnostics;
7. необязательные структурные улучшения.

Для каждого backend PR выполнить как минимум:

```bash
cd backend
npm run typecheck
npm run lint
npm run format:check
npm run test
npm run test:e2e
npm run build
```

DB-backed и E2E проверки требуют PostgreSQL согласно `backend/AGENTS.md`. Для изменений repository query shape snapshot updates допустимы только осознанно, с ручной проверкой diff. Для error work отдельно проверить, что HTTP и gRPC не раскрывают driver details.

## Что не предлагается

- Не переносить repository interfaces в frameworks: это заставит use cases зависеть от adapter layer и отменит dependency inversion.
- Не ловить все `QueryFailedError` в каждом repository одинаковым `InternalError`: неизвестные сбои лучше централизованно логировать на transport boundary, сохраняя cause внутри процесса.
- Не вводить generic repository/criteria DSL вместо TypeORM: это сохранит неглубокий интерфейс и ORM-мышление во внутренних слоях.
- Не удалять SQL snapshot tests вместе с `queryDetails`: нужно поменять test seam, а не потерять защиту query shape.
- Не объединять V1/V2 только ради уменьшения дублирования до фиксации различий контрактов.

## Итоговая оценка

Основное направление уже частично правильное: API вызывает use cases, PostgreSQL adapters реализуют интерфейсы из внутреннего слоя, а framework production-код не импортирует API/usecases. Главный архитектурный долг сосредоточен в качестве seam: ORM-специфичные criteria, SQL diagnostics, service locator и `unknown` transaction context делают repository interface шире и опаснее необходимого.

Первым исправлением должна быть атомарная идемпотентность, потому что это реальный риск повторного side effect. Затем стоит очистить dependency rule и согласовать единую error model. Только после этого безопасно углублять repository/transaction modules и заниматься локальной структурой.
