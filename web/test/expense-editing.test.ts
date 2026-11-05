import './helpers/register-source.ts';
import assert from 'node:assert/strict';
import test from 'node:test';
import type {Expense} from '../src/5-entities/expense/types/types.ts';
const {ExpenseType} = await import('../src/5-entities/expense/constants.ts');
const {getExpenseEditForm, buildExpenseRevision, canEditExpense} =
  await import('../src/5-entities/expense/lib/expense-editing.ts');
const {createEvent} = await import('../src/5-entities/event/services/api.ts');
const {ExpenseService} = await import('../src/5-entities/expense/services/expense-service.ts');
const {httpClient} = await import('../src/6-shared/api/http-client.ts');
const {expenseStore} = await import('../src/5-entities/expense/stores/expense-store.ts');
const {eventStore} = await import('../src/5-entities/event/stores/event-store.ts');
const {userStore} = await import('../src/5-entities/user/stores/user-store.ts');
const {analyticsService} = await import('../src/6-shared/analytics/analytics-service.ts');
const expense: Expense = {
  id: 'expense-1',
  version: 3,
  eventId: 'trip',
  expenseType: ExpenseType.Expense,
  description: 'Dinner',
  userWhoPaidId: 'a',
  currencyId: 'usd',
  isCustomRate: false,
  createdAt: '2026-10-01T10:00:00.000Z',
  updatedAt: '2026-10-02T10:00:00.000Z',
  splitInformation: [
    {userId: 'a', amount: 10, exchangedAmount: 9.12},
    {userId: 'b', amount: 10, exchangedAmount: 9.12},
  ],
};
const trip = {
  id: 'trip',
  version: 2,
  name: 'Trip',
  pinCode: '1234',
  currencyId: 'eur',
  users: [
    {id: 'a', eventId: 'trip', name: 'A'},
    {id: 'b', eventId: 'trip', name: 'B'},
    {id: 'c', eventId: 'trip', name: 'C'},
  ],
};
const revised = {...expense, version: 4, description: 'Lunch', updatedAt: '2026-10-03T10:00:00.000Z'};
test.beforeEach(() => {
  process.env.NEXT_PUBLIC_EXPENSE_EDITING_ENABLED = 'true';
  eventStore.setCurrentEvent(trip);
  userStore.setUsers(trip.users);
  expenseStore.setExpenses([expense]);
  expenseStore.setSelectedExpenseForDetails(expense);
});
test.afterEach(() => {
  delete process.env.NEXT_PUBLIC_EXPENSE_EDITING_ENABLED;
});

test('hides editing unless explicitly enabled and event and expense versions are supported', () => {
  for (const value of [undefined, '', 'false', '1', 'TRUE']) {
    if (value === undefined) {
      delete process.env.NEXT_PUBLIC_EXPENSE_EDITING_ENABLED;
    } else {
      process.env.NEXT_PUBLIC_EXPENSE_EDITING_ENABLED = value;
    }
    assert.equal(canEditExpense(trip, expense), false);
  }
  process.env.NEXT_PUBLIC_EXPENSE_EDITING_ENABLED = 'true';
  assert.equal(canEditExpense(trip, expense), true);
  for (const version of [undefined, 1, 3]) {
    assert.equal(canEditExpense({...trip, version}, expense), false);
  }
  for (const version of [undefined, 0, 1.5]) {
    assert.equal(canEditExpense(trip, {...expense, version}), false);
  }
  assert.equal(canEditExpense(trip, {...expense, eventId: 'other'}), false);
});
test('creates version-2 events only with the flag and preserves the original draft', async (t) => {
  const draft = {name: 'Trip', currencyId: 'eur', pinCode: '1234', users: [{name: 'A'}]};
  const bodies: unknown[] = [];
  t.mock.method(httpClient, 'request', async (url: string, init: RequestInit) => {
    assert.equal(url, '/user/event');
    bodies.push(JSON.parse(init.body as string));
    return trip;
  });
  delete process.env.NEXT_PUBLIC_EXPENSE_EDITING_ENABLED;
  await createEvent(draft, 'off');
  process.env.NEXT_PUBLIC_EXPENSE_EDITING_ENABLED = 'true';
  await createEvent(draft, 'on');
  assert.deepEqual(bodies, [draft, {...draft, version: 2}]);
  assert.equal('version' in draft, false);
});
test('prefills explicit saved shares without adding participants or rounding the historical rate', () => {
  const form = getExpenseEditForm(expense, 'eur');
  assert.equal(form.splitOption, '2');
  assert.equal(form.amount, 20);
  assert.deepEqual(form.splitInformation, [
    {userId: 'a', amount: 10},
    {userId: 'b', amount: 10},
  ]);
  assert.ok(Math.abs(form.exchangeRate! - 0.912) < 1e-12);
  assert.equal(form.isCustomRate, false);
});
test('preserves exact saved exchange amounts for description and payer-only changes', () => {
  const custom = {
    ...expense,
    isCustomRate: true,
    splitInformation: [
      {userId: 'a', amount: 3, exchangedAmount: 2.74},
      {userId: 'b', amount: 7, exchangedAmount: 6.39},
    ],
  };
  const body = buildExpenseRevision(
    custom,
    {...getExpenseEditForm(custom, 'eur'), description: 'Lunch', userWhoPaidId: 'b'},
    'eur',
    ['a', 'b', 'c'],
  );
  assert.deepEqual(body, {
    expectedVersion: 3,
    description: 'Lunch',
    userWhoPaidId: 'b',
    currencyId: 'usd',
    createdAt: '2026-10-01T10:00:00.000Z',
    isCustomRate: true,
    splitInformation: [
      {userId: 'a', amount: 3, exchangedAmount: 2.74},
      {userId: 'b', amount: 7, exchangedAmount: 6.39},
    ],
  });
});
test('requests historical automatic conversion when financial inputs change', () => {
  const form = getExpenseEditForm(expense, 'eur');
  form.splitInformation[0].amount = 12;
  const body = buildExpenseRevision(expense, form, 'eur', ['a', 'b']);
  assert.equal(body.isCustomRate, false);
  assert.deepEqual(body.splitInformation, [
    {userId: 'a', amount: 12},
    {userId: 'b', amount: 10},
  ]);
  assert.equal(body.createdAt, '2026-10-01T10:00:00.000Z');
});
test('sends explicit custom amounts and clears custom intent for same-currency edits', () => {
  const form = {...getExpenseEditForm(expense, 'eur'), isCustomRate: true, exchangeRate: 1.25};
  assert.deepEqual(buildExpenseRevision(expense, form, 'eur', ['a', 'b']).splitInformation, [
    {userId: 'a', amount: 10, exchangedAmount: 12.5},
    {userId: 'b', amount: 10, exchangedAmount: 12.5},
  ]);
  const same = buildExpenseRevision(expense, {...form, currencyId: 'eur'}, 'eur', ['a', 'b']);
  assert.equal(same.isCustomRate, false);
  assert.deepEqual(same.splitInformation, [
    {userId: 'a', amount: 10},
    {userId: 'b', amount: 10},
  ]);
});
test('distributes equal and percentage edits without losing a cent', () => {
  const form = {...getExpenseEditForm(expense, 'eur'), amount: 10, splitOption: '1'};
  assert.deepEqual(buildExpenseRevision(expense, form, 'eur', ['a', 'b', 'c']).splitInformation, [
    {userId: 'a', amount: 3.34},
    {userId: 'b', amount: 3.33},
    {userId: 'c', amount: 3.33},
  ]);
  const percentages = {
    ...form,
    splitOption: '3',
    splitInformation: [
      {userId: 'a', amount: 33},
      {userId: 'b', amount: 67},
    ],
  };
  assert.deepEqual(buildExpenseRevision(expense, percentages, 'eur', ['a', 'b']).splitInformation, [
    {userId: 'a', amount: 3.3},
    {userId: 'b', amount: 6.7},
  ]);
});
test('rejects duplicate participants, invalid amounts, invalid percentages and unknown payers', () => {
  const form = getExpenseEditForm(expense, 'eur');
  for (const data of [
    {
      ...form,
      splitInformation: [
        {userId: 'a', amount: 1},
        {userId: 'a', amount: 2},
      ],
    },
    {...form, splitInformation: [{userId: 'a', amount: NaN}]},
    {...form, splitInformation: []},
    {...form, userWhoPaidId: 'missing'},
    {...form, splitOption: '3', splitInformation: [{userId: 'a', amount: 99}]},
    {...form, isCustomRate: true, exchangeRate: 0},
  ]) {
    assert.throws(() => buildExpenseRevision(expense, data, 'eur', ['a', 'b']));
  }
});
test('retries with one idempotency key and replaces the expense without create analytics', async (t) => {
  const calls: Array<{url: string; init: RequestInit & {idempotencyKey?: string}}> = [];
  t.mock.method(httpClient, 'request', async (url: string, init: RequestInit & {idempotencyKey?: string}) => {
    calls.push({url, init});
    if (calls.length === 1) {
      throw {statusCode: 503};
    }
    return revised;
  });
  const events: unknown[] = [];
  t.mock.method(analyticsService, 'track', (e: unknown) => {
    events.push(e);
  });
  await new ExpenseService().updateExpense(
    expense,
    {...getExpenseEditForm(expense, 'eur'), description: 'Lunch'},
    '1234',
  );
  assert.equal(calls.length, 2);
  assert.equal(calls[0].url, '/v4/user/event/trip/expense/expense-1/revisions');
  assert.equal(calls[0].init.method, 'POST');
  assert.ok(calls[0].init.idempotencyKey);
  assert.equal(calls[0].init.idempotencyKey, calls[1].init.idempotencyKey);
  assert.equal(calls[0].init.body, calls[1].init.body);
  assert.deepEqual(JSON.parse(calls[0].init.body as string), {
    pinCode: '1234',
    expectedVersion: 3,
    description: 'Lunch',
    userWhoPaidId: 'a',
    currencyId: 'usd',
    createdAt: '2026-10-01T10:00:00.000Z',
    isCustomRate: false,
    splitInformation: expense.splitInformation,
  });
  assert.equal(expenseStore.expenses.length, 1);
  assert.equal(expenseStore.expenses[0].version, 4);
  assert.equal(expenseStore.selectedExpenseForDetails?.description, 'Lunch');
  assert.equal(expenseStore.isUpdatingExpense, false);
  assert.deepEqual(events, []);
});
test('retains original state and draft on conflicts without retrying or upgrading expectedVersion', async (t) => {
  t.mock.timers.enable({apis: ['setTimeout']});
  const conflict = {statusCode: 409, code: 'B4017', message: 'Expense was revised by another request'};
  let calls = 0;
  t.mock.method(httpClient, 'request', async () => {
    calls++;
    throw conflict;
  });
  const draft = {...getExpenseEditForm(expense, 'eur'), description: 'My draft'};
  await assert.rejects(new ExpenseService().updateExpense(expense, draft, '1234'), (e) => e === conflict);
  for (let attempt = 0; attempt < 2; attempt++) {
    t.mock.timers.tick(2000);
    await new Promise<void>((resolve) => setImmediate(resolve));
  }
  assert.equal(calls, 1);
  assert.equal(expenseStore.expenses[0].version, 3);
  assert.equal(expenseStore.selectedExpenseForDetails?.version, 3);
  assert.equal(draft.description, 'My draft');
  assert.equal(expenseStore.isUpdatingExpense, false);
});
test('blocks writes with the flag off and reloads an explicitly requested latest snapshot', async (t) => {
  let calls = 0;
  t.mock.method(httpClient, 'request', async (url: string) => {
    calls++;
    assert.equal(url, '/v2/user/event/trip/expenses');
    return [revised];
  });
  process.env.NEXT_PUBLIC_EXPENSE_EDITING_ENABLED = 'false';
  await assert.rejects(new ExpenseService().updateExpense(expense, getExpenseEditForm(expense, 'eur'), '1234'));
  assert.equal(calls, 0);
  await new ExpenseService().reloadExpense(expense, '1234');
  assert.equal(expenseStore.selectedExpenseForDetails?.version, 4);
  assert.equal(expenseStore.expenses.length, 1);
});

test('preserves rounded custom amounts when a small saved share converts to zero', () => {
  const saved = {
    ...expense,
    isCustomRate: true,
    splitInformation: [
      {userId: 'a', amount: 0.01, exchangedAmount: 0},
      {userId: 'b', amount: 100, exchangedAmount: 0.67},
    ],
  };
  const form = getExpenseEditForm(saved, 'eur');
  assert.equal(form.exchangeRate, 0.0067);
  const body = buildExpenseRevision(saved, {...form, description: 'New description'}, 'eur', ['a', 'b']);
  assert.deepEqual(body.splitInformation, [
    {userId: 'a', amount: 0.01, exchangedAmount: 0},
    {userId: 'b', amount: 100, exchangedAmount: 0.67},
  ]);
});

test('allows description-only edits when every saved custom conversion rounds to zero', () => {
  const saved = {
    ...expense,
    isCustomRate: true,
    splitInformation: [
      {userId: 'a', amount: 0.01, exchangedAmount: 0},
      {userId: 'b', amount: 0.02, exchangedAmount: 0},
    ],
  };
  const form = getExpenseEditForm(saved, 'eur');
  assert.equal(form.exchangeRate, undefined);
  const body = buildExpenseRevision(saved, {...form, description: 'New description'}, 'eur', ['a', 'b']);
  assert.deepEqual(body.splitInformation, [
    {userId: 'a', amount: 0.01, exchangedAmount: 0},
    {userId: 'b', amount: 0.02, exchangedAmount: 0},
  ]);
  assert.throws(() =>
    buildExpenseRevision(saved, {...form, splitInformation: [{userId: 'a', amount: 1}]}, 'eur', ['a', 'b']),
  );
  assert.deepEqual(
    buildExpenseRevision(saved, {...form, exchangeRate: 0.5, splitInformation: [{userId: 'a', amount: 1}]}, 'eur', [
      'a',
      'b',
    ]).splitInformation,
    [{userId: 'a', amount: 1, exchangedAmount: 0.5}],
  );
});
