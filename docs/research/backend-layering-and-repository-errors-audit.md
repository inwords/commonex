# Аудит слоёв backend и ошибок репозиториев

Проверено: 23.09.2026.

## Цель и границы

Цель аудита — проверить направление зависимостей `api -> usecases -> frameworks -> domain`, устройство интерфейсов репозиториев и путь ошибок от PostgreSQL/TypeORM до HTTP и gRPC. Это план изменений, а не выполненный рефакторинг.

Проверены production-файлы `backend/src/{api,usecases,frameworks,domain,packages}`, Nest-модули, PostgreSQL-адаптеры, связанные тесты и миграции. CodeGraph был выбран первым способом навигации из-за наличия `.codegraph/`, но CLI `codegraph` отсутствует в окружении, а MCP-инструмент CodeGraph/ast-index недоступен. После этой проверки использован точечный fallback через `rg`, `nl` и чтение конкретных файлов.

## Сначала уточнить правило зависимостей

Линейная запись полезна для **runtime-потока**:

```text
HTTP/gRPC -> use case -> repository port -> PostgreSQL adapter -> database
```

Но source-level зависимости при dependency inversion не должны быть той же линейной стрелкой. Рекомендуемая модель:

```text
api/composition ──> usecases ──> domain
       │                            ▲
       └────────> frameworks ───────┘
```

- `domain` не импортирует Nest, TypeORM, API или use cases;
- `usecases` импортирует domain-типы и порты, но не TypeORM и transport-типы;
- `frameworks` реализует порты, определённые внутренним слоем, поэтому импорт `frameworks -> domain` корректен;
- `api` переводит transport DTO/status/metadata в интерфейс use case;
- composition root связывает реализации с портами. Он может знать обо всех слоях, но не должен заставлять бизнес-код знать конкретный адаптер.

Сейчас wiring размещён в [`UseCasesModule`](../../backend/src/usecases/usecases.layer.ts#L1), который импортирует `FrameworksLayer`. Это соответствует буквальной цепочке пользователя, но смешивает бизнес-слой и composition root. Для строгого dependency rule связывание следует поднять в `AppModule` или отдельный composition-модуль. При этом сами use case-классы уже зависят в основном от domain-портов, а PostgreSQL-реализации корректно зависят от их интерфейсов.

## Резюме находок

| Приоритет | Статус | Изменение | Основной риск сейчас |
| --- | --- | --- | --- |
| P0 | Подтверждённый по коду риск; нужна конкурентная репродукция | Сделать идемпотентность атомарной | Два конкурентных запроса могут дважды выполнить операцию, после чего один завершится сырым `23505` |
| P1 | Подтверждённая проблема интерфейса | Убрать transport data из идемпотентности | Use cases знают URL/status, а JSONB replay меняет runtime-типы ответа |
| P1 | Подтверждённая проблема | Убрать TypeORM из domain/usecases | Внутренние слои знают ORM query DSL, dependency rule уже нарушен |
| P1 | Подтверждённая проблема | Ввести единый контракт ожидаемых и неожиданных ошибок | Output use cases неполон, а DB-сбои дают разные HTTP/gRPC ответы |
| P1 | Подтверждённая проблема | Убрать HTTP status из domain | Domain зависит от Nest/HTTP, хотя gRPC использует другую семантику |
| P1 | Подтверждённый пробел | Автоматически проверять импорты между слоями | Текущий ESLint защищает только production от `test-support` |
| P2 | Подтверждённая проблема | Сделать транзакционный seam fail-safe | Неверный `ctx` молча отключает транзакцию |
| P2 | Подтверждённая проблема интерфейса | Убрать `queryDetails` из domain-портов | Production-интерфейс каждого репозитория расширен только ради adapter-тестов |
| P2 | Подтверждённая проблема интерфейса | Разделить общий `RelationalDataServiceAbstract` на capability ports | Каждый use case получает service locator со всеми репозиториями и lifecycle-методами |
| P3 | Подтверждённый долг | Унифицировать классы ошибок и barrel exports | Plain objects теряют stack/cause; публичный export неполон |
| P3 | Вариант дизайна | Переместить чистые event rules из frameworks | Единственная реализация абстракции создаёт неглубокий, гипотетический seam |
| P3 | Вариант дизайна | Развязать gRPC DTO от HTTP DTO | Два transport-адаптера меняются совместно |
| P3 | Требует подтверждения инварианта | Атомарно переиспользовать один активный share token | Конкурентные вызовы могут создать несколько активных токенов |

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

Большинство use cases объявляет `Result<Value, BusinessError>` и возвращает ожидаемые отказы как значение. Затем HTTP/gRPC controllers повторяют `if (isError(result)) throw result.error`, например [`user.controller.ts`](../../backend/src/api/http/user/user.controller.ts#L48).

Одновременно:

- PostgreSQL event repository напрямую бросает `EventOperationConflictError` при коде PostgreSQL `55P03`: [`event.repository.ts`](../../backend/src/frameworks/relational-data-service/postgres/repositories/event.repository.ts#L47);
- `IdempotencySharedUseCase` бросает `IdempotencyHashMismatchError`, хотя оборачивает функцию с generic `Result`: [`idempotency.usecase.ts`](../../backend/src/usecases/shared/idempotency.usecase.ts#L25);
- эти ошибки отсутствуют в объявленных `Output` затронутых use cases, например [`save-event-expense.usecase.ts`](../../backend/src/usecases/users/save-event-expense.usecase.ts#L23) и [`delete-event.usecase.ts`](../../backend/src/usecases/users/delete-event.usecase.ts#L16).

Итог: интерфейс use case не описывает все ожидаемые outcomes. Caller должен знать и `Result`, и скрытые классы исключений.

Рекомендуемая политика:

1. Ожидаемый бизнес-отказ, после которого клиент может изменить запрос или повторить его осознанно, входит в `Result` use case.
2. Адаптер переводит только распознанные vendor errors в нейтральные ошибки порта, например `ResourceBusy` или `IdempotencyKeyConflict`; use case переводит их в business outcome.
3. Неожиданные инфраструктурные сбои остаются исключениями, логируются один раз на transport boundary и получают единый безопасный `INTERNAL_ERROR` без SQL/driver details.
4. Controllers используют один helper/interceptor для распаковки `Result`, а HTTP/gRPC filters отвечают только за transport mapping.

Альтернатива — бросать типизированные application exceptions для всех ожидаемых отказов. Она тоже может быть последовательной, но смешивать оба подхода в одном интерфейсе не следует.

### 4. Неожиданные repository errors не имеют стабильного transport-контракта

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

- use case получает узкие capability ports (`EventReader`, `ExpenseWriter`, `TransactionRunner`), сгруппированные по согласованной операции, а не обязательно один CRUD-interface на таблицу;
- lifecycle остаётся у PostgreSQL adapter/composition module;
- health получает отдельный `HealthProbe` port;
- не создавать по интерфейсу на каждый метод: интерфейс должен скрывать сложность операции и иметь реальную заменяемость либо отчётливую test seam.

### 9. Границы слоёв не проверяются автоматически

В production-настройках ESLint `no-restricted-imports` запрещает только `test-support`: [`eslint.config.js`](../../backend/eslint.config.js#L82). Поэтому импорты TypeORM в domain/usecases проходят CI.

После согласования модели зависимостей добавить scoped rules минимум для:

- `src/domain/**`: запрет `#api`, `#usecases`, `#frameworks`, Nest, TypeORM и transport packages;
- `src/usecases/**`: запрет `#api`, конкретных `#frameworks/**`, TypeORM и transport DTO packages; отдельно решить, временно ли допускается `@Injectable`;
- `src/frameworks/**`: запрет `#api` и `#usecases`;
- production: уже действующий запрет `test-support` сохранить;
- relative imports должны проверяться на тот же escape path, иначе aliases можно обойти через `../../`.

Добавлять правила лучше после первых P1-исправлений, затем включать как CI gate. Иначе baseline сразу станет красным и правило начнут обходить.

### 10. Публичный интерфейс error-модуля неполон

Barrel [`domain/errors/index.ts`](../../backend/src/domain/errors/index.ts#L1) не экспортирует `EventOperationConflictError` и `IdempotencyHashMismatchError`, хотя они входят в `BUSINESS_ERROR_CLASSES`. Поэтому часть файлов импортирует `#domain/errors`, часть — внутренний `#domain/errors/errors`.

После выбора error policy все публичные типы должны экспортироваться через один interface модуля. Если ошибки остаются throwable, общий base class должен наследовать `Error`, вызывать `super(message)` и поддерживать `cause`. Сейчас plain classes намеренно разрешены ESLint-конфигурацией: [`eslint.config.js`](../../backend/eslint.config.js#L37). Nest filters работают, поэтому это не текущий functional bug, но стандартные stack/cause и observability хуже.

## Варианты дизайна и места, требующие решения

### Чистые event rules находятся в frameworks

`EventService` проверяет существование, soft delete и PIN, не использует инфраструктуру и имеет одну реализацию: [`event-service.ts`](../../backend/src/frameworks/event-service/event-service.ts#L9). Абстракция в domain плюс единственный framework adapter образуют гипотетический seam. Предпочтительно переместить реализацию правил в domain как обычный глубокий модуль/функции и внедрять только то, что реально меняется или имеет внешний side effect.

### Supported-currency policy смешан с adapter orchestration

`SupportedCurrencyService` находится в frameworks, фильтрует поддерживаемые коды и оркестрирует два repository ports: [`supported-currency-service.ts`](../../backend/src/frameworks/supported-currency-service/supported-currency-service.ts#L12). Это бизнес-политика, а не внешний adapter. Её следует либо перенести в domain/usecases, либо сформировать отдельный query port, чья PostgreSQL-реализация сразу возвращает поддерживаемые данные. Выбор зависит от того, должна ли политика одинаково работать поверх нескольких adapters.

### gRPC зависит от HTTP DTO

gRPC controller и request DTO импортируют HTTP DTO, например [`user.controller.ts`](../../backend/src/api/grpc/user/user.controller.ts#L21) и [`add-users-to-event-grpc-request.dto.ts`](../../backend/src/api/grpc/user/dto/add-users-to-event-grpc-request.dto.ts#L1). Это не нарушение четырёх верхнеуровневых слоёв, но связывает два transport adapters. Общие transport-neutral input/output types могут жить у use case; HTTP и gRPC должны иметь свои DTO и mapper, даже если поля пока совпадают.

### Один активный share token

Канонический domain doc говорит, что существующий активный token переиспользуется. Use case выполняет `findOneActiveByEventId -> insert` без transaction: [`create-event-share-token-v2.usecase.ts`](../../backend/src/usecases/users/v2/create-event-share-token-v2.usecase.ts#L24). В БД `eventId` имеет неуникальный индекс: [`event-share-token.entity.ts`](../../backend/src/frameworks/relational-data-service/postgres/entities/event-share-token.entity.ts#L5).

Конкурентные вызовы могут создать несколько активных token. До исправления нужно подтвердить строгий инвариант: «ровно один активный token» или «несколько допустимы, но один обычно переиспользуется». В первом случае нужна атомарная repository operation и DB/lock guarantee, во втором — уточнение domain doc и названия `findOneActive...`.

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
2. Заменить transport URL на стабильный operation key и определить versioned response codec, сохраняющий runtime shape.
3. Убрать HTTP `statusCode` из domain record; transport определяет статус по результату операции.
4. Реализовать атомарность в PostgreSQL adapter и перевод `23505` без утечки TypeORM.
5. Сохранить успешный response в той же согласованной lifecycle-модели; определить recovery для `in_progress` после падения процесса.
6. Добавить DB-backed concurrency tests для одинакового и различного hash.
7. Добавить HTTP и gRPC tests на replay, mismatch, одинаковую форму дат и concurrent request.

Критерий готовности: side effect в параллельном тесте выполняется ровно один раз; клиент никогда не видит raw `23505`.

### Этап 2. Очистить dependency rule (P1)

1. Заменить `delete(FindOptionsWhere)` на `deleteExpiredBefore(Date)`.
2. Убрать `LessThan` из use case и TypeORM из domain.
3. Убрать `HttpStatus`/`httpCode` из domain errors; добавить полную HTTP mapping рядом с gRPC mapping.
4. Сделать health output transport-neutral.
5. Перенести Nest module composition из `UseCasesModule` в явный composition root, если принята строгая модель.
6. Добавить ESLint restrictions для слоёв и regression fixtures/tests для запрещённых импортов.

Критерий готовности: `domain` не импортирует Nest/TypeORM; `usecases` не импортирует TypeORM/transport types/concrete frameworks; CI предотвращает возврат нарушений.

### Этап 3. Унифицировать ошибки (P1)

1. Ввести общий transport-neutral тип business failure и полный публичный export.
2. Перевести lock conflict из прямого `EventOperationConflictError` в нейтральный repository/application outcome, затем включить его в use case Output.
3. Перевести idempotency mismatch в тот же выбранный путь.
4. Добавить catch-all HTTP/gRPC filters только для unknown errors, с безопасным `INTERNAL_ERROR`, логированием и correlation id.
5. Убрать повторяющийся `if (isError) throw` из controllers через один transport helper/interceptor.
6. Проверить snapshot/contract tests для каждой строки error matrix.

Критерий готовности: каждый ожидаемый отказ виден в типе use case; неизвестная DB-ошибка имеет одинаковый безопасный внешний контракт в HTTP и gRPC.

### Этап 4. Углубить repository/transaction modules (P2)

1. Перейти к transaction-scoped repositories либо branded fail-fast context.
2. Убрать lifecycle methods из domain data interface.
3. Выделять capability ports по use case, начиная с idempotency и event mutation paths; не переписывать все семь repositories одним большим PR.
4. Убрать `queryDetails` из production interfaces, сохранив SQL snapshot tests через framework-internal hook.
5. После каждого slice запускать repository, use case и E2E tests затронутого потока.

Критерий готовности: запрос невозможно случайно выполнить вне объявленной transaction; production caller не знает о SQL diagnostics и не получает полный service locator без необходимости.

### Этап 5. Локальные улучшения структуры (P3)

1. Переместить чистые event rules из frameworks после удаления гипотетической абстракции.
2. Определить место supported-currency policy.
3. Разделить gRPC и HTTP DTO, оставив общим use case contract.
4. Решить share-token invariant и добавить concurrency test при строгом варианте.
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
