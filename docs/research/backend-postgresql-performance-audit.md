# Аудит производительности PostgreSQL в backend

Проверено: 27.09.2026.

Цель — зафиксировать потенциальные улучшения PostgreSQL-нагрузки backend и дать поэтапный план, не смешивая статически доказанные свойства кода с предположениями о реальном плане выполнения. В рамках проверки обновлён только этот документ; production-код и migrations не изменялись.

## Краткий вывод

Наиболее полезные первые изменения:

1. добавить индексы для двух основных дочерних выборок: `expense(event_id)` и `user_info(event_id)`; в одном синтетическом прогоне sparse lookups ускорились примерно в 168 раз, а hot lookup — в 8 раз, но production-эффект всё ещё нужно измерить отдельно;
2. исправить запрос активного share token: явно ограничить результат одной строкой и проверить составной индекс `(event_id, expires_at DESC)`; синтетический benchmark подтвердил, что именно комбинация `LIMIT 1` и составного индекса устраняет чтение всех token одного event;
3. исправить fan-out в последнем запросе Grafana: текущий join расходов и участников перемножает строки и одновременно увеличивает стоимость запроса и искажает счётчики;
4. ввести pagination и стабильный порядок для выдачи расходов/участников до того, как отдельные события станут большими;
5. измерять DB latency, pool saturation и планы запросов: сейчас есть tracing `pg` и число соединений, но нет законченного набора сигналов для выбора индексов и pool size.

Классического N+1 в production use cases не найдено. Основные read flows выполняют фиксированное число запросов, не по одному запросу на каждый объект. Сильная сторона текущей конфигурации — уже заданные server/client query timeouts и `idle_in_transaction_session_timeout`.

## Статусы утверждений

- **Подтверждено статически** — видно непосредственно в source, migrations, query snapshots или production dashboard SQL.
- **Измерено на локальной БД** — получено на предоставленной базе `expense`; она почти пустая и не репрезентативна для performance-выводов.
- **Измерено синтетически** — получено на временных таблицах PostgreSQL 18.6 с контролируемой cardinality; подтверждает механизм, но не production latency.
- **Гипотеза, нужен `EXPLAIN`** — логически подходящий кандидат, но PostgreSQL может выбрать другой план в зависимости от объёма и распределения данных.

Аудит подключился к предоставленной локальной PostgreSQL 18.6 read-only запросами. В ней только 1 event, 2 user rows и нет expenses/tokens/idempotency keys, поэтому её планы фиксируют текущую схему, но не доказывают production performance. Дополнительно выполнен изолированный синтетический benchmark во временных таблицах с итоговым `ROLLBACK`; его результаты показывают поведение запросов при заданной cardinality, но не заменяют `EXPLAIN (ANALYZE, BUFFERS)` на репрезентативной копии production. Production compose использует PostgreSQL 17, то есть локальный benchmark также отличается major version. PostgreSQL рекомендует проверять индексы на реальной нагрузке и актуальной статистике через `ANALYZE`/`EXPLAIN ANALYZE`: [официальная документация](https://www.postgresql.org/docs/17/indexes-examine.html).

## Проверенная поверхность

- семь TypeORM entities и все шесть migrations в `backend/migrations/default`;
- семь PostgreSQL repositories и сохранённые Jest query snapshots;
- production use cases, использующие эти repositories;
- TypeORM/node-postgres configuration и timeout layering;
- idempotency/share-token lifecycle;
- OpenTelemetry initialization и Grafana DB panels;
- SQL всех панелей `infra/grafana/sync/expenses-analytics.json`;
- фактическая схема, индексы, ограничения, объёмы, orphan checks и планы SELECT на локальной базе;
- изолированный synthetic benchmark для event reads, active-token lookup и Top Events.

Основные локальные источники:

- [PostgreSQL config](../../backend/src/frameworks/relational-data-service/postgres/config.ts)
- [runtime defaults](../../backend/src/config.ts)
- [entities](../../backend/src/frameworks/relational-data-service/postgres/entities)
- [migrations](../../backend/migrations/default)
- [repositories](../../backend/src/frameworks/relational-data-service/postgres/repositories)
- [repository query snapshots](../../backend/src/frameworks/relational-data-service/postgres/repositories/__tests__/__snapshots__)
- [analytics dashboard SQL](../../infra/grafana/sync/expenses-analytics.json)
- [OpenTelemetry setup](../../backend/src/otel.ts)

## Фактические измерения

### Предоставленная локальная БД

Подключение: `localhost:5432`, database `expense`, schema `public`, PostgreSQL 18.6. Пароль в документе не сохраняется. Все проверки фактической схемы и данных выполнялись внутри `BEGIN READ ONLY`.

Воспроизводимая команда (при экспортированных `POSTGRES_*` из локального окружения):

```bash
PGPASSWORD="$POSTGRES_PASSWORD" psql \
  -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" \
  -U "$POSTGRES_USER_NAME" -d "$POSTGRES_DATABASE" \
  -X -v ON_ERROR_STOP=1 -P pager=off
```

Основные read-only запросы:

```sql
BEGIN READ ONLY;
SET LOCAL search_path TO public;

SELECT 'event' AS table_name, count(*) FROM event
UNION ALL SELECT 'expense', count(*) FROM expense
UNION ALL SELECT 'user_info', count(*) FROM user_info
UNION ALL SELECT 'event_share_token', count(*) FROM event_share_token
UNION ALL SELECT 'idempotency_keys', count(*) FROM idempotency_keys;

SELECT schemaname, tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
ORDER BY tablename, indexname;

SELECT conrelid::regclass AS table_name, conname, contype,
       pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE connamespace = 'public'::regnamespace
ORDER BY conrelid::regclass::text, conname;

SELECT current_setting('server_version') AS server_version,
       current_setting('max_connections') AS max_connections,
       current_setting('shared_buffers') AS shared_buffers;

SELECT extname FROM pg_extension ORDER BY extname;

SELECT 'event.currency_id' AS relation, count(*) AS orphan_rows
FROM event e LEFT JOIN currency c ON c.id = e.currency_id WHERE c.id IS NULL
UNION ALL SELECT 'user_info.event_id', count(*)
FROM user_info u LEFT JOIN event e ON e.id = u.event_id WHERE e.id IS NULL
UNION ALL SELECT 'expense.event_id', count(*)
FROM expense x LEFT JOIN event e ON e.id = x.event_id WHERE e.id IS NULL
UNION ALL SELECT 'expense.currency_id', count(*)
FROM expense x LEFT JOIN currency c ON c.id = x.currency_id WHERE c.id IS NULL
UNION ALL SELECT 'event_share_token.event_id', count(*)
FROM event_share_token t LEFT JOIN event e ON e.id = t.event_id WHERE e.id IS NULL
UNION ALL SELECT 'expense.user_who_paid_id', count(*)
FROM expense x LEFT JOIN user_info u ON u.id = x.user_who_paid_id WHERE u.id IS NULL;

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM expense WHERE event_id = (SELECT id FROM event LIMIT 1);

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM user_info WHERE event_id = (SELECT id FROM event LIMIT 1);

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM event_share_token
WHERE event_id = (SELECT id FROM event LIMIT 1) AND expires_at >= now()
ORDER BY expires_at DESC;

COMMIT;
```

Результат на момент проверки:

| Факт | Значение |
| --- | ---: |
| `event` | 1 row |
| `user_info` | 2 rows |
| `expense` | 0 rows |
| `event_share_token` | 0 rows |
| `idempotency_keys` | 0 rows |
| `max_connections` | 100 |
| `shared_buffers` | 128 MB |
| FK constraints | 0 |
| найденные orphan rows по перечисленным в разделе 10 связям | 0 |

Схема содержит все 6 migrations. Фактические индексы совпали с migrations: у `expense` и `user_info` только PK; у `event_share_token` есть PK и одиночный `event_id`; у `idempotency_keys` — PK и `expires_at`. `pg_stat_statements` не установлен; из extensions присутствует только `plpgsql`.

Read-only `EXPLAIN (ANALYZE, BUFFERS)` на текущих данных показал `Seq Scan` для пустого `expense` (0.010 ms), `Seq Scan` для двух строк `user_info` (0.008 ms) и bitmap scan с sort для пустого active-token lookup (0.032 ms). Это корректные планы для почти пустых relations, а не аргумент за или против индексов.

### Изолированный синтетический прогон

Для проверки механизма отдельно выполнен один прогон в `TEMP` tables PostgreSQL 18.6: `BEGIN`, генерация данных через `generate_series`, `ANALYZE`, `EXPLAIN (ANALYZE, BUFFERS, TIMING OFF)`, затем `ROLLBACK`. Никакая постоянная таблица не изменялась. Формулы набора:

```sql
-- expense: 200k rows; event 1 = 20k rows; остальные распределены по 10k events;
-- payload = repeat('x', 120).
SELECT gs,
       CASE WHEN gs <= 20000 THEN 1 ELSE 2 + ((gs - 20001) % 10000) END,
       repeat('x', 120)
FROM generate_series(1, 200000) AS gs;

-- user_info: 100k rows; event 1 = 10k rows; остальные по 10k events;
-- payload = repeat('x', 80).
SELECT gs,
       CASE WHEN gs <= 10000 THEN 1 ELSE 2 + ((gs - 10001) % 10000) END,
       repeat('x', 80)
FROM generate_series(1, 100000) AS gs;

-- tokens: 200k rows; event 1 = 5k rows; expires_at равномерно вокруг now().
SELECT gs,
       CASE WHEN gs <= 5000 THEN 1 ELSE 2 + ((gs - 5001) % 10000) END,
       now() + ((gs % 7200) - 3600) * interval '1 minute',
       repeat('x', 80)
FROM generate_series(1, 200000) AS gs;

-- Top Events: 1k events × 100 expenses × 20 users;
-- у каждого event 10 custom-rate expenses.
SELECT event_id, expense_no, expense_no <= 10 AS is_custom_rate
FROM generate_series(1, 1000) AS event_id
CROSS JOIN generate_series(1, 100) AS expense_no;

SELECT event_id, user_no
FROM generate_series(1, 1000) AS event_id
CROSS JOIN generate_series(1, 20) AS user_no;

-- Индексы проверялись по одному изменению формы:
CREATE INDEX ON expense_bench (event_id);
CREATE INDEX ON user_bench (event_id);
CREATE INDEX ON token_bench (event_id);
CREATE INDEX ON token_bench (event_id, expires_at DESC);
```

Результаты одного прогона, не latency distribution:

| Query/fixture | До | После | Наблюдение |
| --- | ---: | ---: | --- |
| `expense`, sparse event (18 rows) | seq scan 7.217 ms | index scan 0.043 ms | индекс селективен |
| `expense`, hot event (20k rows) | seq scan 7.709 ms | bitmap index scan 0.958 ms | planner использует индекс и при 10% relation |
| `expense`, missing event | — | index scan 0.004 ms | быстрый negative lookup |
| `user_info`, sparse event (9 rows) | seq scan 3.051 ms | index scan 0.018 ms | индекс селективен |
| token, event=1 (5k total, 1401 active), только `event_id`, без `LIMIT` | 0.311 ms | — | читает все подходящие token |
| тот же token query + `LIMIT 1` | 0.186 ms | — | всё ещё просмотрено 5k rows, 3599 отброшено filter |
| `(event_id, expires_at DESC)` + `LIMIT 1` | — | 0.016 ms, 4 buffers | индекс сразу даёт первую active row |
| Top Events, 1k × 100 × 20 | 242.959 ms, 2m joined rows и temp I/O | pre-aggregate 8.075 ms | примерно 30.1× быстрее |

На Top Events старый запрос для одного event сообщал 2000 expenses и 200 custom rates вместо 100 и 10; pre-aggregate вернул корректные значения. Эти измерения подтверждают query-shape проблемы, но не являются production benchmark: отличаются данные, hardware, concurrency, cache state и major version PostgreSQL.

## Приоритеты

| Приоритет | Статус | Изменение | Ожидаемый эффект |
| --- | --- | --- | --- |
| P0 | Подтверждено статически и синтетически; production effect не измерен | Индексы `expense(event_id)` и `user_info(event_id)` | Ускоряет селективные event reads и помогает join/group-by аналитики; planner может предпочесть seq scan при низкой селективности |
| P0 | Подтверждено статически и синтетически | `LIMIT 1` для active-token lookup; проверить индекс `(event_id, expires_at DESC)` | Не загружать и не гидратировать все активные токены события; составной индекс позволяет остановиться на первой строке без отдельной сортировки |
| P0 | Подтверждено статически и синтетически | Устранить fan-out в запросе Top Events Grafana | Исправляет неверные счётчики и сокращает промежуточный набор с `expenses × users` |
| P1 | Подтверждено статически | Pagination + стабильный `ORDER BY` для expenses/users | Ограничивает DB/сеть/heap Node.js на одном запросе и делает страницы воспроизводимыми |
| P1 | Подтверждено статически; operational impact требует метрик | Cleanup expired share tokens и пакетный cleanup idempotency keys | Ограничивает рост таблиц и всплески WAL/dead tuples/lock duration |
| P1 | Подтверждено статически | DB observability: latency/errors/pool wait и slow-query evidence | Позволяет принимать решения об индексах и размере pool по данным |
| P2 | Гипотеза, нужен workload | Настраиваемый pool size и проверка health-check при saturation | Снижает риск очереди к pool либо избыточных соединений |
| P2 | Гипотеза, нужен `EXPLAIN` | Индексы для временных срезов аналитики | Может ускорить селективные dashboard ranges; широкие агрегации всё равно могут предпочесть seq scan |
| P2 | Подтверждено отсутствие ограничений; дизайн требует решения | Добавить FK после orphan audit и определения delete policy | Защищает целостность; индексы referencing columns нужны отдельно |

## 1. Индексы основных event reads

**Статус: подтверждено статически и синтетически; production-выигрыш не измерен.**

Оба пользовательских read path сначала читают event по PK, затем получают полную дочернюю коллекцию:

- `GetEventExpenses` вызывает `expense.findByEventId(eventId)`;
- `GetEventInfo` вызывает `userInfo.findByEventId(eventId)`.

Snapshots фиксируют SQL `WHERE expense.event_id = :eventId` и `WHERE user_info.event_id = :eventId`, но migrations создают для обеих таблиц только PK по `id`. В entities также нет `@Index` для `eventId`: [expense repository](../../backend/src/frameworks/relational-data-service/postgres/repositories/expense.repository.ts), [user repository](../../backend/src/frameworks/relational-data-service/postgres/repositories/user-info.repository.ts), [initial migration](../../backend/migrations/default/1750591686449-init.ts).

Без подходящего индекса planner может только сканировать relation; фактическая стоимость зависит от размера и селективности. Синтетический прогон выше подтвердил пользу первых кандидатов на 100k/200k rows:

```sql
CREATE INDEX idx__expense__event_id ON expense (event_id);
CREATE INDEX idx__user_info__event_id ON user_info (event_id);
```

Они также совпадают с ключами join/group-by в большинстве analytics queries. Однако на маленьких таблицах PostgreSQL закономерно может выбрать sequential scan; это не доказывает бесполезность индекса на production-размере. Официальное описание trade-off: индекс ускоряет поиск, но добавляет общий write/storage overhead, поэтому использовать его нужно осмысленно ([PostgreSQL 17: indexes](https://www.postgresql.org/docs/17/indexes.html)).

### Проверка

Сравнить планы до/после на нескольких формах распределения: маленькое событие, большое событие и отсутствующий `event_id`.

```sql
ANALYZE expense;
ANALYZE user_info;

EXPLAIN (ANALYZE, BUFFERS, WAL, FORMAT TEXT)
SELECT * FROM expense WHERE event_id = '<large-event-id>';

EXPLAIN (ANALYZE, BUFFERS, WAL, FORMAT TEXT)
SELECT * FROM user_info WHERE event_id = '<large-event-id>';
```

Зафиксировать не только execution time, но `actual rows`, ошибку cardinality estimate, `shared hit/read`, размер индекса и изменение insert cost.

## 2. Active share-token lookup читает больше одной строки

**Статус: подтверждено статически и синтетически.**

`findOneActiveByEventId` строит:

```sql
WHERE event_id = :eventId
  AND expires_at >= :now
ORDER BY expires_at DESC
```

и затем вызывает `getOne()`: [repository](../../backend/src/frameworks/relational-data-service/postgres/repositories/event-share-token.repository.ts), [snapshot](../../backend/src/frameworks/relational-data-service/postgres/repositories/__tests__/__snapshots__/event-share-token.repository.spec.ts.snap). В snapshot нет `LIMIT 1`. У закреплённой в [package.json](../../backend/package.json) версии TypeORM 1.1.1 `getOne()` вызывает `getRawAndEntities()` и лишь затем берёт `results.entities[0]`; он сам не добавляет limit: [официальный source TypeORM 1.1.1](https://github.com/typeorm/typeorm/blob/1.1.1/src/query-builder/SelectQueryBuilder.ts). Значит, при нескольких активных токенах база может вернуть, драйвер передать, а ORM гидратировать все подходящие строки.

Сейчас существует только индекс `event_share_token(event_id)`: [migration](../../backend/migrations/default/1767464629981-init.ts). Составной B-tree `(event_id, expires_at DESC)` соответствует equality по ведущей колонке и range/order по второй. PostgreSQL объясняет преимущество leading equality в multicolumn B-tree и возможность удовлетворить `ORDER BY ... LIMIT` непосредственно индексом: [multicolumn indexes](https://www.postgresql.org/docs/17/indexes-multicolumn.html), [indexes and ORDER BY](https://www.postgresql.org/docs/17/indexes-ordering.html).

Минимальный план:

1. добавить `.limit(1)`/`.take(1)` и обновить snapshot осознанно;
2. на объёме с несколькими expired/active tokens сравнить текущий индекс с `(event_id, expires_at DESC)`;
3. только после `EXPLAIN` заменить одиночный индекс составным, если он действительно закрывает workload.

Если будет принят строгий инвариант «один активный токен», его конкурентная реализация рассматривается в [архитектурном аудите](backend-layering-and-repository-errors-audit.md); это отдельный вопрос корректности, не основание игнорировать `LIMIT 1`.

## 3. Grafana Top Events создаёт декартово размножение внутри event

**Статус: подтверждено статически и синтетически; это одновременно correctness и performance defect.**

Последняя панель `expenses-analytics.json` делает два независимых one-to-many join:

```sql
event
LEFT JOIN expense  ON expense.event_id = event.id
LEFT JOIN user_info ON user_info.event_id = event.id
```

Для события с `E > 0` расходами и `U > 0` участниками промежуточный набор содержит `E × U` строк; если одна сторона пуста, из-за `LEFT JOIN` получается `max(E, U, 1)` строк. `COUNT(ex.id)` и `COUNT(*) FILTER (WHERE ex.is_custom_rate = true)` поэтому зависят от числа участников и показывают завышенные значения при `U > 1`; запрос также агрегирует существенно больше строк, чем нужно. `COUNT(DISTINCT ui.id)` маскирует ошибку только для числа участников.

Предпочтительный shape — сначала агрегировать каждую дочернюю таблицу до одной строки на `event_id`, затем присоединить два результата к event. Альтернатива с несколькими `COUNT(DISTINCT ...)` исправит часть счётчиков, но не устранит сам большой fan-out.

После переписывания проверить на fixture: 2 expenses, 3 users, 1 custom expense. Ожидается `Expenses = 2`, `Users = 3`, `Custom Rates = 1`, а не шесть промежуточных строк. Затем сравнить `EXPLAIN (ANALYZE, BUFFERS)` старого и нового SQL.

## 4. Pagination и детерминированный порядок

**Статус: подтверждено статически.**

Production endpoints возвращают все расходы или всех участников события без `LIMIT` и `ORDER BY`: [expense query](../../backend/src/frameworks/relational-data-service/postgres/repositories/expense.repository.ts), [user query](../../backend/src/frameworks/relational-data-service/postgres/repositories/user-info.repository.ts). Это пока может быть приемлемо для маленького события, но верхней границы в repository/API contract нет.

Риски роста:

- полное чтение и передача `expense.split_information` JSONB;
- entity hydration каждой строки TypeORM;
- большой response и heap pressure в Node.js;
- отсутствие гарантированного порядка между одинаковыми запросами;
- невозможность эффективно продолжить загрузку на клиенте.

TypeORM официально рекомендует pagination для ограничения объёма и отмечает cursor pagination как более эффективный вариант для меняющихся данных: [pagination](https://dev.typeorm.io/docs/performance-optimization/pagination/). Для CommonEx сначала нужно согласовать server-client contract. Вероятный cursor — `(created_at, id)` с явным tie-breaker и соответствующим индексом `(event_id, created_at, id)`, но выбирать его до продуктового решения и `EXPLAIN` преждевременно.

Вспомогательные repository `findAll({limit})` используются главным образом тестами. Их отсутствие `ORDER BY` ухудшает детерминизм тестовых выборок, но это не production bottleneck и не должно быть P0.

## 5. N+1 и лишние round trips

**Статус: отсутствие классического N+1 подтверждено статически; latency не измерена.**

Ни один просмотренный use case не выполняет repository call в цикле по полученным сущностям. Основные потоки имеют фиксированную форму:

| Flow | DB round trips до idempotency | Комментарий |
| --- | ---: | --- |
| Get expenses | 2 | event по PK, затем expenses по event |
| Get event info по PIN | 2 | event, затем users |
| Get event info по token | 3 | event, token, users |
| Create share token | 2–3 | event, active token, иногда insert |
| Create event | 3 внутри transaction | currency, insert event, batch insert users |
| Save users V1 | 2 без transaction | event, batch insert users |
| Save users V2 | 2 внутри transaction | locked event, batch insert users |
| Save expense V1, same currency/custom rate | 2 внутри transaction | locked event, insert expense |
| Save expense V1, automatic rate | 5 внутри transaction | locked event, две currency reads, rate read, insert |
| Save expense V2, same currency/custom rate | 2 внутри transaction | locked event, insert expense |
| Save expense V2, automatic rate | 5 внутри transaction | locked event, две currency reads, rate read, insert |

Idempotent mutation дополнительно выполняет lookup idempotency key до core flow и insert успешного результата после него: [idempotency use case](../../backend/src/usecases/shared/idempotency.usecase.ts).

Не следует автоматически объединять все запросы в большой join. Отдельный event read обеспечивает раннюю авторизацию/validation, а collections нужны полностью. Сначала измерить вклад network/database latency. Наиболее очевидные безопасные сокращения:

- active-token `LIMIT 1`;
- отдельный предметный query для token access, если три round trips окажутся значимыми;
- один query для currency/rate lookup только после сравнения с текущими PK reads;
- не заменять entity reads на raw проекции там, где endpoint действительно использует все поля.

В V3 уже есть хороший пример узкой raw projection: version query выбирает только `rateUpdatedAt` и агрегат timestamps currencies, не гидратируя полные entities: [currency-rate repository](../../backend/src/frameworks/relational-data-service/postgres/repositories/currency-rate.repository.ts). TypeORM также рекомендует raw results и явный `select`, когда полные objects не нужны: [официальная документация](https://typeorm.io/docs/performance-optimization/efficient-use-of-query-builder/).

## 6. Транзакции и длительность locks

**Статус: форма подтверждена статически; lock duration не измерена.**

V2 add-users, оба save-expense flow (V1 и V2) и delete-event берут `SELECT ... FOR UPDATE NOWAIT` на строке event. Это fail-fast, поэтому запрос не ждёт обычный `lock_timeout`; при конфликте PostgreSQL возвращает `55P03`, который repository переводит в domain error. Lock удерживается до завершения callback transaction.

Самые длинные callback — automatic-rate ветки обоих save-expense flow: после lock выполняются две последовательные currency reads, currency-rate read, вычисление split и insert. На весь этот промежуток одна mutation удерживает lock, а другие операции с `NOWAIT` fail-fast с `55P03`; они не выстраиваются в очередь. Для сохранения правила «после delete нельзя записывать новые данные» lock оправдан, но его длительность следует сделать наблюдаемой.

План проверки:

1. добавить span/metric вокруг transaction callback и отдельно вокруг ожидания pool connection;
2. нагрузить один hot event и набор разных events;
3. считать `55P03`, statement/lock timeout и p50/p95/p99 transaction duration;
4. только затем решать, можно ли безопасно вынести неизменяемые currency/rate reads перед event lock либо заменить seam более узкой атомарной DB-операцией.

Текущий код правильно передаёт transaction-scoped `EntityManager` во все запросы callback. Это соответствует обязательному правилу TypeORM: внутри transaction использовать предоставленный manager ([TypeORM transactions](https://dev.typeorm.io/docs/transactions/)). Архитектурный риск fail-open transaction context описан отдельно в [аудите слоёв](backend-layering-and-repository-errors-audit.md).

## 7. Рост idempotency keys и share tokens

### Idempotency keys

**Статус: lifecycle подтверждён статически; локально измерено 0 rows. Production-объём неизвестен.**

- TTL записи — 24 часа: [value object](../../backend/src/domain/value-objects/idempotency-key.value-object.ts).
- Cleanup запускается ежедневно в UTC и делает один `DELETE WHERE expires_at < now()`: [scheduler](../../backend/src/api/cron/currency-rate-scheduler.controller.ts), [use case](../../backend/src/usecases/cron/cleanup-idempotency-keys.usecase.ts).
- Индекс `expires_at` существует: [migration](../../backend/migrations/default/1776518011496-init.ts).

При нормальной работе запись может жить почти 48 часов: 24 часа TTL плюс почти сутки до следующего cleanup. Это не ошибка. Риск появляется при большом mutation volume: один неограниченный DELETE создаёт большой transaction/WAL burst и dead tuples, которые позднее должен обработать autovacuum. PostgreSQL не удаляет obsolete row versions физически сразу; их пространство возвращает `VACUUM`: [routine vacuuming](https://www.postgresql.org/docs/17/routine-vacuuming.html).

До изменения cleanup измерить:

```sql
SELECT count(*) AS total,
       count(*) FILTER (WHERE expires_at < now()) AS expired,
       pg_total_relation_size('idempotency_keys') AS total_bytes
FROM idempotency_keys;

SELECT relname, n_live_tup, n_dead_tup, last_autovacuum, last_autoanalyze
FROM pg_stat_user_tables
WHERE relname IN ('idempotency_keys', 'event_share_token');
```

Если cleanup стабильно удаляет большой объём, перейти к небольшим повторяемым batches с лимитом и наблюдаемой длительностью, сохранив индекс по `expires_at`. Не применять `VACUUM FULL` как регулярный cleanup: он требует тяжёлой блокировки; штатный путь — autovacuum/обычный `VACUUM`.

### Event share tokens

**Статус: подтверждено отсутствие cleanup; локально измерено 0 expired и 0 active tokens.**

Token живёт 14 дней, но production-код не удаляет expired tokens. Repository имеет `deleteByToken`, однако use cases его не вызывают. Таблица поэтому монотонно растёт при создании новых токенов, даже если active-token query исключает expired rows.

Добавить отдельный retention policy: периодический batch cleanup либо удаление expired rows при создании нового token. Если cleanup фильтрует только `expires_at`, составной индекс с ведущим `event_id` его полноценно не заменяет; необходимость отдельного `expires_at` индекса нужно подтвердить планом и объёмом.

Для обоих cron jobs нужен явный multi-instance contract.

**Подтверждено wiring:** production compose объявляет оба backend services — blue и green; nginx одновременно перечисляет оба имени в HTTP и gRPC upstream: [compose](../../infra/docker-compose-prod.yml), [nginx config](../../infra/nginx/nginx-prod.conf). Каждый запущенный backend process импортирует `ScheduleModule.forRoot()`, затем `ApiModule -> CronModule`, который регистрирует `CurrencyRateSchedulerController`: [AppModule](../../backend/src/app.module.ts), [ApiModule](../../backend/src/api/api.layer.ts), [CronModule](../../backend/src/api/cron/cron.module.ts). Оба daily handlers зарегистрированы без distributed/singleton lock: [scheduler](../../backend/src/api/cron/currency-rate-scheduler.controller.ts).

Следствие этого wiring: **если одновременно живы N backend processes, каждый из них локально запустит обе daily jobs**, поэтому DB cleanup и внешний currency-rate API call масштабируются с числом процессов. Nginx routing этого не предотвращает, поскольку cron не зависит от входящего запроса.

**Не измерено:** аудит не подключался к production host и не подтверждает, что blue и green фактически были одновременно запущены в конкретный момент или сколько replicas реально работает. Нужно проверить runtime deployment lifecycle. Если одновременный запуск штатный, добавить singleton/advisory lock либо вынести jobs во внешний scheduler; lock должен охватывать и cleanup, и currency fetch/upsert.

## 8. Connection pool и timeouts

**Статус: значения подтверждены статически; соответствие нагрузке не измерено.**

На один backend process настроено:

| Параметр | Значение |
| --- | ---: |
| pool `max` | 5 |
| pool `min` | 1 |
| connection timeout | 10 s |
| idle timeout | 300 s |
| TCP keepalive initial delay | 30 s |
| PostgreSQL `statement_timeout` | 15 s |
| PostgreSQL `lock_timeout` | 3 s |
| `idle_in_transaction_session_timeout` | 30 s |
| client `query_timeout` | 17 s |

Timeout layering разумен: server statement timeout срабатывает раньше client query timeout. `idle_in_transaction_session_timeout` ограничивает забытые idle transactions; PostgreSQL указывает, что такие transactions удерживают locks и мешают vacuum очищать dead tuples: [client connection defaults](https://www.postgresql.org/docs/17/runtime-config-client.html).

Проблемы, которые нельзя решить статически:

- pool `max=5` и все timeouts захардкожены, несмотря на env-подобные имена;
- при двух одновременно работающих backend instances верхняя граница application pool — 10 connections, не считая migrations/Grafana;
- неизвестны production p95 concurrent DB operations, pool wait и PostgreSQL `max_connections`/memory budget; локально измерены `max_connections=100` и `shared_buffers=128MB`;
- health check выполняет `SELECT 1` через тот же DataSource, а Docker health timeout равен 10 секундам; при saturation это может дать ложный статус `unhealthy`, но нужна репродукция. `restart: unless-stopped` сам по себе не рестартует контейнер только из-за health status; restart возможен лишь при внешнем watchdog/deployment automation, которого в repo не найдено.

node-postgres создаёт pool lazily; `min=1` не открывает соединение заранее, а лишь не удаляет уже созданные idle clients ниже min. При полном pool новые requests ждут в FIFO queue; доступны `totalCount`, `idleCount`, `waitingCount`: [официальный Pool API](https://node-postgres.com/apis/pool). Поэтому менять `max` «на глаз» нельзя.

План sizing:

1. измерить `waitingCount`, acquire duration, active/idle connections и request concurrency;
2. учесть число одновременно запущенных replicas и Grafana datasource;
3. сверить суммарный worst case с `SHOW max_connections` и memory limit DB container (сейчас 300 MB);
4. вынести pool/timeouts в валидируемую конфигурацию только если нужен environment-specific tuning;
5. отдельно нагрузить `/health` при занятом pool и решить, нужен ли reserved connection/другой readiness budget.

TypeORM документирует `extra` как прямую передачу options underlying driver; текущий mapping на pg поэтому является поддерживаемым механизмом: [DataSource options](https://typeorm.io/docs/data-source/data-source-options/), [Postgres driver](https://typeorm.io/docs/drivers/postgres/).

## 9. Аналитические запросы

**Статус: SQL подтверждён; рекомендации для временных срезов требуют отдельного workload и `EXPLAIN`.**

Grafana напрямую выполняет полные counts, time-window aggregations, distinct users, group by `event_id` и joins. Кроме уже найденного fan-out, кандидаты для исследования:

- `event(created_at) WHERE deleted_at IS NULL` для селективных time ranges;
- `user_info(created_at)` для временных рядов;
- `expense(created_at)` либо partial `(created_at) WHERE expense_type = 'expense'`;
- `expense(event_id)` и `user_info(event_id)` для joins/grouping;
- более специализированные `(event_id, created_at)` только если last-expense query действительно дорог.

Не добавлять все эти индексы одновременно. Cumulative/all-time panels читают большую долю таблицы, и sequential scan может быть дешевле. Индекс особенно полезен при `ORDER BY ... LIMIT` или селективном range, но не гарантирует ускорение широкой агрегации: [indexes and ORDER BY](https://www.postgresql.org/docs/17/indexes-ordering.html), [EXPLAIN](https://www.postgresql.org/docs/17/sql-explain.html).

Dashboard статически задаёт `refresh: 1h`. Перед увеличением интервала измерить total time/calls, latency и влияние ручных refresh. Для больших объёмов следующий уровень — rollup/materialized view, но только после измерения нагрузки и freshness requirement.

## 10. Foreign keys и целостность

**Статус: отсутствие FK подтверждено статически и по catalog; на текущих локальных данных orphan rows не найдено.**

Во всех migrations отсутствуют `FOREIGN KEY`, а entities хранят связи как обычные `varchar`. Потенциальные связи:

- `event.currency_id -> currency.id`;
- `user_info.event_id -> event.id`;
- `expense.event_id -> event.id`;
- `expense.currency_id -> currency.id`;
- `event_share_token.event_id -> event.id`;
- `expense.user_who_paid_id -> user_info.id`.

Это прежде всего data-integrity gap, а не гарантированное ускорение SELECT. Перед добавлением FK нужно:

1. найти orphan rows;
2. определить `ON DELETE` policy с учётом soft-delete event;
3. отдельно решить инвариант «payer принадлежит тому же event» — простой FK по user id этого не доказывает;
4. добавить/проверить индексы referencing columns до операций delete/update parent;
5. измерить write overhead.

PostgreSQL не создаёт индекс на referencing columns автоматически и рекомендует рассматривать его, поскольку delete/update referenced row иначе ищет dependents сканированием: [PostgreSQL 17: constraints](https://www.postgresql.org/docs/17/ddl-constraints.html). Поэтому FK migration и index migration следует проектировать вместе, но не путать их цели.

## 11. Query logging и observability

**Статус: подтверждено статически.**

Что уже есть:

- `application_name = commonex-backend`;
- auto-instrumentation `@opentelemetry/instrumentation-pg`;
- Grafana panel `Backend DB Connections` по `db.client.connection.count`;
- server/client timeouts;
- query snapshots, фиксирующие generated SQL на тестах.

Чего не найдено:

- TypeORM query/error logging в runtime (`logging: false`);
- `maxQueryExecutionTime`/структурированный slow-query logger;
- dashboard p50/p95/p99 DB query duration и error rate по low-cardinality operation;
- pool wait/acquire duration и queued requests;
- `pg_stat_statements` в compose/config;
- dashboard autovacuum/dead tuples/table/index size;
- сохранённые production-like `EXPLAIN` baselines для ключевых queries.

Не включать полное query logging в production без фильтрации: оно шумное и может раскрывать parameters. Более безопасный порядок:

1. убедиться, что pg spans содержат operation/table и duration без PIN/token/request payload;
2. построить панели DB duration/error и pool saturation;
3. добавить slow-query logging с redaction и threshold;
4. при необходимости включить `pg_stat_statements` с контролем extension/config и доступа;
5. завести regression fixtures/benchmarks для top queries.

TypeORM поддерживает отдельный `maxQueryExecutionTime` и выбор категорий logging, включая только `error`: [официальная logging documentation](https://typeorm.io/docs/logging/). Это механизм, а не готовая рекомендация включить SQL/parameters без privacy review.

## Поэтапный план изменений

### Зафиксированный scope

Сейчас запланированы следующие задачи из списка, согласованного 27.09.2026:

- [x] **P0-1. Исправить Grafana Top Events:** предварительно агрегировать expenses и users, добавить SQL-fixture корректности.
- [x] **P0-2. Ограничить active share-token query:** добавить `LIMIT 1`, обновить snapshot и repository test.
- [x] **P0-3. Добавить составной индекс share token:** проверить и добавить `(event_id, expires_at DESC)` вместе с P0-2.
- [x] **P0-4. Добавить индексы дочерних таблиц:** `expense(event_id)` и `user_info(event_id)` через migration с проверкой планов.
- [x] **P1-8. Удалять просроченные share tokens:** определить retention policy и реализовать bounded batch cleanup.
- [x] **P1-9. Сделать cleanup idempotency keys пакетным:** ограничить размер batch и добавить наблюдаемую длительность/счётчик.
- [x] **P1-10. Защитить cron jobs от двойного запуска:** обеспечить singleton semantics для cleanup и обновления курсов в blue/green deployment.

Отложены на следующий этап: воспроизводимый DB benchmark как отдельный инструмент (№5), pagination расходов и участников (№6–7), DB observability и slow-query monitoring (№11–12), настраиваемые pool/timeouts и saturation-проверка health endpoint (№13–14), foreign keys и дальнейшая оптимизация Grafana (№15–16).

Статус `[x]` означает, что задача реализована; финальный прогон тестов оставлен владельцу репозитория.

### Этап 0. Получить production-like baseline, без schema changes — отложено

1. Поднять только throwaway test DB штатной командой.
2. Применить migrations и загрузить synthetic dataset с несколькими распределениями event size.
3. Выполнить `ANALYZE`.
4. Снять `EXPLAIN (ANALYZE, BUFFERS, WAL)` для event expenses/users, active token и всех dashboard queries.
5. Зафиксировать relation/index sizes, live/dead tuples и pool metrics.

Критерий готовности: у каждого кандидата есть baseline plan, cardinality и target metric; production data не изменялись.

### Этап 1. Закрыть статически подтверждённые P0 — текущий scope

1. Переписать Top Events dashboard через предварительные aggregates; добавить fixture correctness check (P0-1).
2. Добавить `.limit(1)` для active-token query и repository test, доказывающий SQL shape (P0-2).
3. Проверить и добавить составной индекс `(event_id, expires_at DESC)` для active-token lookup (P0-3).
4. Добавить migrations для `expense(event_id)` и `user_info(event_id)` после сравнения baseline/after (P0-4).
5. Для каждой migration повторить целевые plans и insert benchmark; не принимать её, если target plan/metric не улучшился на репрезентативном fixture.

Критерий готовности: token query возвращает из DB не более одной строки; dashboard counts корректны; основные event reads используют ожидаемый plan на large-event fixture.

### Этап 2. Ограничить рост данных и responses — частично текущий scope

1. **Отложено:** согласовать cursor contract expenses/users с Android/Web и backward compatibility (№6–7).
2. **Отложено:** добавить стабильный order, page size cap и нужный composite index по результатам `EXPLAIN` (№6–7).
3. Ввести retention и bounded batch cleanup для expired share tokens (P1-8).
4. Перевести cleanup idempotency keys на bounded batches и добавить наблюдаемую длительность/счётчик (P1-9).
5. Обеспечить singleton semantics scheduler в blue/green lifecycle для обоих cleanup и currency fetch/upsert (P1-10).

Критерий готовности текущего scope: cleanup не создаёт длинный transaction/WAL spike, expired rows удаляются ограниченными batches, а scheduled jobs выполняются не более чем одним backend process. Ограничение размера responses относится к отложенным №6–7.

### Этап 3. Сделать tuning измеряемым — отложено

1. Добавить DB duration/error/pool-wait dashboards.
2. Добавить безопасный slow-query signal.
3. Настроить pool по числу replicas и измеренной concurrency.
4. Проверить health endpoint при saturation.
5. После orphan audit принять решение по FK/delete policy.

Критерий готовности: index/pool/timeout изменения опираются на наблюдаемые метрики, а не на defaults или единичный локальный тест.

### Этап 4. Оптимизировать аналитику только при доказанной необходимости — отложено

1. Ранжировать dashboard queries по total time/calls.
2. Добавлять по одному индексу, каждый раз сравнивая read benefit с write/storage cost.
3. Увеличить текущий refresh interval `1h`, только если total time/calls это оправдывает, либо внедрить rollups/materialized views для дорогих all-time aggregates.
4. Удалять неиспользуемые индексы только после достаточного окна наблюдения.

Критерий готовности: dashboard укладывается в latency/load budget, а write paths не деградировали.

## Команды верификации

Выполнять из `backend/`. Команды запуска/остановки DB приведены для явного ручного шага; этот аудит их не выполнял.

```bash
docker compose -f docker-compose.test.yml up --wait db
npm run db:migrate
npm run typecheck
npm run lint:check
npm run format:check
npm run test
npm run test:e2e
npm run build
docker compose -f docker-compose.test.yml down -v
```

Для repository query-shape изменение snapshot допускается только намеренно:

```bash
npx jest --runInBand -u src/frameworks/relational-data-service/postgres/repositories/__tests__/event-share-token.repository.spec.ts
git diff -- src/frameworks/relational-data-service/postgres/repositories/__tests__/__snapshots__
```

Для schema/index verification:

```sql
SELECT schemaname, tablename, indexname, indexdef
FROM pg_indexes
WHERE tablename IN ('event', 'expense', 'user_info', 'event_share_token', 'idempotency_keys')
ORDER BY tablename, indexname;

SELECT relname, seq_scan, seq_tup_read, idx_scan, n_live_tup, n_dead_tup,
       last_autovacuum, last_autoanalyze
FROM pg_stat_user_tables
WHERE relname IN ('event', 'expense', 'user_info', 'event_share_token', 'idempotency_keys')
ORDER BY relname;

SELECT relname, indexrelname, idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
WHERE relname IN ('event', 'expense', 'user_info', 'event_share_token', 'idempotency_keys')
ORDER BY relname, indexrelname;
```

`EXPLAIN ANALYZE` действительно выполняет statement и добавляет profiling overhead; применять его только к безопасным SELECT на test/копии. Для write queries использовать transaction с rollback на throwaway DB либо ограничиться `EXPLAIN` без `ANALYZE`: [PostgreSQL EXPLAIN](https://www.postgresql.org/docs/17/sql-explain.html).

## Что не следует делать без дополнительных данных

- Не добавлять индекс на каждую колонку из `WHERE`/`ORDER BY`.
- Не увеличивать pool только потому, что запросы стоят в очереди: причиной могут быть медленные queries или длинные transactions.
- Не включать полное SQL/parameter logging без redaction review.
- Не переносить неизменяемые reads из locked transaction без проверки race с delete/event policy.
- Не вводить offset pagination как окончательный контракт для активно меняющейся большой коллекции без сравнения с cursor.
- Не добавлять FK до orphan audit и определения `ON DELETE` semantics.
- Не считать plan на пустой/маленькой test DB доказательством production performance.

## Итог

Текущий persistence слой прост и не демонстрирует классического N+1, а timeout layering уже заметно лучше defaults. Главный риск — рост: основные дочерние event queries пока не имеют индексов и bounds, expired share tokens не очищаются, а observability недостаточна для уверенного tuning. Самая конкретная ошибка уже сейчас находится в analytics SQL: one-to-many joins перемножают строки.

Рекомендуемый порядок: сначала baseline и исправление P0 query shapes, затем bounded responses/retention, после этого pool и analytics tuning по измерениям. Такой порядок даёт проверяемое улучшение без набора спекулятивных индексов.
