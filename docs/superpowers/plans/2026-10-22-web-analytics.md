# Web Analytics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Подключить Метрику к web для источников трафика и воронок текущих пользовательских действий без передачи содержимого поездок и credentials.

**Architecture:** Типизированный модуль в `6-shared/analytics`, клиентский наблюдатель внутри существующего BrowserRouter, ручные просмотры и события после окончательного результата операций. AppMetrica на Android остаётся; общая групповая аналитика требует отдельной backend/mobile задачи.

**Tech Stack:** Next.js 16 static export, React 19, React Router 7, TypeScript, native Node test runner, официальный JS tag Метрики без npm-wrapper.

**Spec:** [Решение и контракт событий](../../product/2026-10-web-analytics-design.md). [Исследование](../../research/2026-10-web-analytics-provider-research.md).

## Global Constraints

- Planning track: Feature Track; реализация выполнена в `feat/web-analytics`, от `origin/main` `c3a64f02`.
- Implementation Readiness: PASS для реализации выключенной интеграции. Production activation BLOCKED до фактического counter ID, настройки кабинета, policy и сетевой приёмки. Это условия выпуска, не повод блокировать написание кода.
- Не отправлять имена, описания, суммы, PIN, токены, raw event/user/expense IDs, Error.message и содержимое чеков.
- Production + разрешённый hostname + валидный ID + согласие; иначе ни загрузки SDK, ни очереди аналитики.
- `schema_version: 1`, `platform: web`; точные имена событий и enum — из spec.
- Не менять API, identity-flow или deep links. Не править существующие посторонние изменения backend и docs.
- Сохранить feature-sliced направление зависимостей; shared analytics не импортирует entities/features/widgets.
- Проверить актуальные официальные API при реализации: major версии Next/React Router новее типичного training cutoff.

## Review Focus

1. Secret-bearing URL: unit sanitizer tests и обязательный сетевой сценарий задачи 5, включая фоновые SDK-запросы.
2. StrictMode/re-render и retries: задачи 2–3 проверяют один просмотр navigation entry и один итог операции.
3. Отказ/отзыв согласия, storage denied, adblock: задачи 1–2 проверяют отсутствие отправки и работоспособность продукта.
4. UTM до перехода/согласия и два разных browser user: задачи 1, 2, 5 проверяют источник и обе независимые воронки.
5. Скопированная ссылка, несколько поездок, выбранный участник против плательщика: задачи 3–5 не приписывают неизвестный канал и не заявляют CAT.

## Файлы и ответственность

Создать `web/src/6-shared/analytics/analytics-event.ts` (один публичный union контракт), `analytics-url.ts` (чистая нормализация), `analytics-service.ts` (SDK lifecycle/очередь/отправка), `analytics-consent.ts` (storage и подписки), `index.ts` (публичный экспорт).

Создать `web/src/3-widgets/WebAnalytics/WebAnalytics.tsx` (BrowserRouter observer + запуск), `web/src/3-widgets/AnalyticsConsent/AnalyticsConsent.tsx` (выбор и изменение). Изменить `web/src/app/page.tsx` для подключения внутри BrowserRouter. Не помещать observer в ClientProviders, который находится вне этого роутера.

Другие точные файлы перечислены у задач. Не создавать платформенную абстракцию для двух неизвестных будущих SDK и постоянный first-touch storage.

## Task 1: Безопасный контракт и сервис Метрики

**Files:** Create перечисленные пять shared-файлов; create `web/test/analytics-url.test.ts`, `web/test/analytics-service.test.ts`, `web/test/analytics-consent.test.ts`.

**Interfaces:**
- `AnalyticsEvent` — discriminated union `{name, params}` из spec, без произвольного `Record<string, unknown>` для вызывающего кода.
- `sanitizeAnalyticsUrl(rawUrl: string, origin: string, includeCampaign: boolean): string` — абсолютный URL с `/`, `/support`, `/event`, `/other`; query только из реестра меток первого просмотра, hash отсутствует.
- `sanitizeAnalyticsReferrer(rawReferrer: string, origin: string): string` — внешний origin / внутренний нормализованный URL / пустая строка.
- `getAnalyticsConsent(): 'unknown' | 'granted' | 'denied'`; `setAnalyticsConsent(value: 'granted' | 'denied'): void`; `subscribeAnalyticsConsent(listener: () => void): () => void`. Ошибка storage не ломает текущий in-memory выбор; при следующей загрузке unknown.
- Публичные `initializeAnalytics(): void`, `track(event: AnalyticsEvent): void`, `pageview(url: string, referrer: string): void`, `stopAnalytics(): void`; все fail-safe. Конфигурация и browser transport передаются конструктору `AnalyticsService` для тестов, singleton экспортирует публичные функции.

- [ ] Сначала написать unit tests: `/event/secret?token=SECRET#SECRET` → `/event`; `token`, PIN, неизвестные query и UTM-текст удалены; разрешённые `telegram/messenger/travel_launch` сохранены только при includeCampaign=true; malformed referrer → пусто; внешний referrer лишён path/query.
- [ ] Добавить tests: невалидный ID, неподходящий host, dev, unknown/denied consent → transport не вызван; throw от SDK не выходит в приложение; очередь максимум 50/TTL 30 секунд; init/hit предшествуют queued goals; failed script очищает очередь; repeat init не добавляет script; stop прекращает отправку.
- [ ] Добавить tests согласия: reload сохраняет отказ/разрешение, storage throw не ломает UI, до согласия нет analytics ID/storage кроме самого выбора.
- [ ] Из `web/` выполнить `npm test`; убедиться, что новые тесты падают по отсутствующей реализации.
- [ ] Реализовать интерфейсы и runtime whitelist event payload. SDK загружать асинхронно с официального URL только после gates. Настройки из spec; дополнительно проверить актуальные `trackHash`/`sendTitle` и отключить их. Статические titles: `CommonEx`, `CommonEx — Trip`, `CommonEx — Support`, `CommonEx`.
- [ ] Выполнить `npm test`; все существующие и новые тесты проходят.

## Task 2: Согласие и просмотры реального SPA

**Files:** Create два widget-файла выше; modify `web/src/app/page.tsx`, `web/src/3-widgets/AppFooter/AppFooter.tsx`, `web/src/6-shared/i18n/locales/ru.ts`, `web/src/6-shared/i18n/locales/en.ts`; create `web/test/analytics-navigation.test.ts` и при необходимости чистый helper рядом с WebAnalytics.

**Consumes:** Task 1 lifecycle, URL и consent functions.
**Produces:** Одна subscription к location/consent; доступный RU/EN выбор аналитики; сохранённый только в памяти безопасный landing context.

- [ ] Unit-test чистого navigation coordinator: первое посещение, повтор эффекта с тем же location.key, forward/back, другой entry с тем же нормализованным `/event`, неизвестный route и переход до согласия. First landing UTM не теряются; текущий маршрут после согласия отправляется отдельно лишь если изменился.
- [ ] Проверить red → реализовать наблюдение через React Router location внутри BrowserRouter, не Next pathname. Не считать каждый render просмотром; не использовать глобальный once-флаг, который потеряет реальные повторные посещения.
- [ ] Добавить понятные «Разрешить»/«Отказаться» и постоянный доступ к изменению выбора. Клавиатура и screen reader должны работать; тексты не объясняют SDK/флаги. Отзыв вызывает stop, сохраняет denied и перезагружает приложение без SDK.
- [ ] Выполнить `npm test`, `npm run lint`; в dev вручную проверить refusal/reload/storage unavailable и работу создания/входа без разрешения аналитики.

## Task 3: Бизнес-события текущих сценариев

**Files:** Modify `web/src/5-entities/event/services/event-service.ts`, `web/src/5-entities/expense/services/expense-service.ts`, `web/src/2-pages/MainPage.tsx`, `web/src/2-pages/EventPage.tsx`, `web/src/4-features/FindEvent/ui/FindEventForm.tsx`, `web/src/3-widgets/SelectUserList/SelectUserList.tsx`, `web/src/3-widgets/EventHeader/EventHeader.tsx`, `web/src/3-widgets/EventTabs/EventTabs.tsx`, `web/src/3-widgets/AppFooter/AppFooter.tsx`; create `web/test/analytics-outcomes.test.ts`.

**Consumes:** Task 1 `track(AnalyticsEvent)`.
**Produces:** События всей таблицы v1 в spec. Entry context `created | token | manual` хранится в EventStore (modify `web/src/5-entities/event/stores/event-store.ts`) после успешного входа/создания; route effect не создаёт второй join для created/manual entry.

- [ ] Сначала проверить service behavior tests с fake transport/API: retry then success → один success; три failed attempts → один failed; исходная ошибка продолжает возвращаться вызывающему коду; SDK throw не превращает успешное сохранение в failure. При необходимости добавить минимальные constructor injection seams для API/track/retry, не менять глобально HTTP client ради тестов.
- [ ] Добавить tests: create не порождает join; token/manual дают верный entry_method; параметры никогда не содержат request/response/message/IDs; возврат не отправляет expense_submitted; reason ограничен enum.
- [ ] После red внедрить success/failure снаружи retry в сервисах; не ставить instrumentation на fetchExpenses или каждый setCurrentEvent. Для getEventInfo добавить явный entry method в существующий params/options контракт локального сервиса (API payload не менять) и обновить оба caller.
- [ ] В UI отправлять open/click/claim в явных действиях; invitation_opened один раз на вход, token request не означает copied. Проверить boolean результата `copy`. Запрос токена/clipboard failure не должны отправлять invitation_copied.
- [ ] При копировании дописать только фиксированные `commonex/referral/trip_invite`, не наследовать landing UTM. App store click измеряет нажатие без задержки перехода; событие не обещает установку.
- [ ] Выполнить `npm test`; вручную пройти creator/token/manual, смену выбранного участника, открытие долгов, расход и возврат. Проверить отсутствие raw ID и финансовых данных в переданном track payload.

## Task 4: Поставка конфигурации и описание обработки данных

**Files:** Modify `web/Dockerfile`, `web/nginx.conf`, `.github/workflows/main.yml`, `web/public/privacy.html`, `web/public/privacy-ru.html`; create `web/docs/analytics.md`.

**Consumes:** `NEXT_PUBLIC_YANDEX_METRICA_ID`, `NEXT_PUBLIC_ANALYTICS_ALLOWED_HOSTS` (разделённый запятыми список точных hostname). ID — публичный идентификатор, не API key.
**Produces:** Docker build ARG → ENV до `npm run build`; GitHub repository variables передаются только frontend-ветке matrix. Пустой ID/host list оставляет интеграцию выключенной. Никаких env/secrets файлов в коммите.

- [ ] Перед изменением CI прочитать актуальные root/infra инструкции для затронутой поверхности; сверить build-push-action input с официальной документацией.
- [ ] Добавить build args и конкретный выбор frontend в matrix workflow; существующая backend сборка не получает web configuration. Production vars не включать в обычные локальные тесты.
- [ ] Настроить исходящую Referrer-Policy без path/query, включая статические privacy страницы. Не считать это заменой очистке SDK payload.
- [ ] Обновить RU/EN policy до активации: web provider, цели, категории данных/cookies, выбор и отзыв, фактическое хранение и доступ к информации. Не включать кодовые флаги, параметры URL и технические инструкции в публичный текст. Если точные условия провайдера не подтверждены, выпуск остаётся выключен; не писать выдуманные сроки.
- [ ] Runbook: создание отдельного web counter, отключение автоматических функций в кабинете, все goal IDs из spec, hostname/vars, отчёты из spec, настройки retention/deletion, проверка и rollback. Не вставлять API keys; не менять настройки аккаунта в рамках написания кода.
- [ ] Из `web/`: `npm test`, `npm run lint`, `npm run build`. Собрать frontend image с тестовыми build args; проверить, что финальный artifact действительно содержит test ID, а сборка без ID не инициирует загрузку SDK. Runtime env не представлять как работающую конфигурацию static export.

## Task 5: Приёмка и включение

**Files:** Update `web/docs/analytics.md` фактическими результатами; код менять только при обнаружении конкретных дефектов предыдущих задач.

**Consumes:** Готовая сборка, отдельный QA counter, goals и доступ редактора отчётов.
**Produces:** Проверенный baseline источников и браузерных воронок либо конкретный blocked release с выключенным production counter.

- [ ] В браузере с разрешённой сетью проверить исходящие SDK запросы на тестовом `/event/TEST_ID?token=TEST_SECRET`, с секретами в hash/referrer/title/форме. Проверить загрузку script, init, hit, reachGoal, params, фоновые запросы и HTTP Referer; секреты, raw ID и содержимое формы отсутствуют. Использовать вымышленные данные, HAR с production credentials не сохранять.
- [ ] Повторить без согласия, после отказа/отзыва, при adblock/отключённом storage: до разрешения SDK не загружается; продукт работает; после отзыва не отправляет.
- [ ] В двух независимых browser contexts открыть размеченную Telegram campaign и скопированное приглашение; выполнить разные пути. В QA counter проверить источник первого визита, goal IDs, отсутствие дублей, причины ошибок и обе воронки. Не ожидать, что организатор и приглашённый образуют одну пользовательскую воронку.
- [ ] Проверить back/forward, reload token entry, два разных события, creator → share → manual/token join и clipboard false; не объявлять несколько браузеров разными реальными людьми.
- [ ] Создать четыре отчёта из spec; сохранить фактические настройки окна/атрибуции и ссылку на QA evidence в runbook. Для 48-часового окна не требовать ждать 48 часов: проверить настройку и короткий тестовый путь.
- [ ] Перед production включением подтвердить policy, cookies/retention, hostname и сетевой gate. Если кабинет/ID пока недоступны, отметить непроверенные шаги явно и оставить feature disabled. Выпускные полномочия не выводить из запроса на исследование и план.
- [ ] Rollback: убрать counter ID и пересобрать/redeploy frontend; runtime env недостаточно. Немедленное прекращение сбора при инциденте требует доступного механизма отключения на стороне провайдера, возможность проверить до активации. Не обещать kill switch для уже открытых вкладок.

## Самопроверка плана

Первый выпуск отвечает на «откуда пришли и на каком шаге уходят» для web. Полный roadmap dashboard web/mobile, CAT, повторная поездка за 90 дней и referral между людьми явно требуют следующего System Track. События ещё не существующих фич не подменяются синтетическими успехами. Production gate отделён от готовности к разработке. Тесты плана покрывают credentials, неработающую аналитику, retries, SPA-навигацию и ограничения attribution.

## Состояние исполнения 24 октября 2026

- [x] Tasks 1–3: адаптер, URL/consent/navigation, события и тесты.
- [x] Task 4: Docker/CI build-time config, privacy RU/EN, runbook.
- [x] Локальная production-сборка с тестовым счётчиком и без конфигурации; Docker image с тестовым счётчиком собирается.
- [x] Browser privacy audit: воспроизведена и исправлена утечка исходного URL в техническом init-запросе. Исправление описано в design/runbook; отдельный regression test проверяет safe init.
- [x] Native tests: 31 passing, включая реальный сценарий отказа storage для LanguageStore.
- [x] Независимое code review: нет Important/Critical замечаний; отсутствие автоматической очистки cookies явно отражено в design/runbook.
- [ ] Task 5 account-dependent: QA counter delivery, goals/reports, фактические retention/settings и production activation — доступа к кабинету и ID нет, сбор выключен по умолчанию.

Дельты: API getEventInfo больше не скрывает исходную ошибку; LanguageStore переносит отказ browser storage; init дополнительно получает безопасные url/referrer. Бизнес-контракты backend и Android не изменены.

## Дельта 25 октября: упрощение структуры

Историческое разбиение задач выше заменено: один `analytics-service.ts` объединяет configuration, consent, navigation, SDK loading, URL minimization и failure classification; `analytics-event.ts` остаётся контрактом. Пять вспомогательных файлов и index wrapper удалены. Сервисы и UI обращаются к `analyticsService`. Тесты согласия и навигации теперь проверяют этот же сервис. Поведение сбора и защиты данных сохраняется. Build-time конфигурация не требует безусловного script tag в HTML: SDK загружается после согласия. В web ESLint закреплены обязательные фигурные скобки.
