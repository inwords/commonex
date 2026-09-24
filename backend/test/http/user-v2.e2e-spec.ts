import {CurrencyCode} from '#domain/entities/currency.entity';
import {ExpenseType, IExpense} from '#domain/entities/expense.entity';
import {ExpenseValueObject} from '#domain/value-objects/expense.value-object';

import {createEvent, findCurrencyIdByCode, insertTodayRate} from '../support/fixtures';
import {TestApp, createTestApp} from '../support/test-app';

describe('HTTP /v2/user', () => {
  let testApp: TestApp;
  let usdId: string;
  let eurId: string;

  beforeAll(async () => {
    testApp = await createTestApp();
  });

  afterAll(async () => {
    await testApp.close();
  });

  beforeEach(async () => {
    await testApp.reset();
    usdId = await findCurrencyIdByCode(testApp.rDataService, CurrencyCode.USD);
    eurId = await findCurrencyIdByCode(testApp.rDataService, CurrencyCode.EUR);
  });

  it('returns event info by pin code via POST', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    const response = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}`,
      payload: {pinCode: '1234'},
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({id: event.id});
  });

  it('issues a share token and accepts it instead of the pin code', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    const tokenResponse = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}/share-token`,
      payload: {pinCode: '1234'},
    });
    const {token} = tokenResponse.json<{token: string; expiresAt: string}>();
    const infoResponse = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}`,
      payload: {token},
    });

    expect(tokenResponse.statusCode).toBe(201);
    expect(token).toMatch(/^[0-9a-f]{64}$/);
    expect(infoResponse.statusCode).toBe(200);
    expect(infoResponse.json()).toMatchObject({id: event.id});
  });

  it('rejects an unknown share token with 401 B4008', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    const response = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}`,
      payload: {token: 'not-a-token'},
    });

    expect(response.statusCode).toBe(401);
    expect(response.json()).toEqual({statusCode: 401, code: 'B4008', message: 'Invalid token'});
  });

  it('converts an expense in another currency using the rate of the day', async () => {
    await insertTodayRate(testApp.rDataService, {USD: 1, EUR: 0.85});
    const event = await createEvent(testApp.app, {currencyId: usdId});
    const [alice, bob] = event.users;

    const response = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}/expense`,
      payload: {
        pinCode: '1234',
        description: 'Dinner',
        userWhoPaidId: alice?.id,
        currencyId: eurId,
        expenseType: ExpenseType.Expense,
        splitInformation: [
          {userId: alice?.id, amount: 40},
          {userId: bob?.id, amount: 60},
        ],
      },
    });

    expect(response.statusCode).toBe(201);
    expect(response.json()).toMatchObject({
      isCustomRate: false,
      splitInformation: [
        {userId: alice?.id, amount: 40, exchangedAmount: 47.06},
        {userId: bob?.id, amount: 60, exchangedAmount: 70.59},
      ],
    });
  });

  it('rejects a foreign-currency expense with 404 B4005 when no rate exists for today', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});
    const [alice] = event.users;

    const response = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}/expense`,
      payload: {
        pinCode: '1234',
        description: 'Dinner',
        userWhoPaidId: alice?.id,
        currencyId: eurId,
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: alice?.id, amount: 40}],
      },
    });

    expect(response.statusCode).toBe(404);
    expect(response.json()).toMatchObject({code: 'B4005'});
  });

  it('rejects a partially custom rate with 400 B4010', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});
    const [alice, bob] = event.users;

    const response = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}/expense`,
      payload: {
        pinCode: '1234',
        description: 'Dinner',
        userWhoPaidId: alice?.id,
        currencyId: eurId,
        expenseType: ExpenseType.Expense,
        splitInformation: [
          {userId: alice?.id, amount: 40, exchangedAmount: 50},
          {userId: bob?.id, amount: 60},
        ],
      },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({code: 'B4010'});
  });

  it('lists expenses only with the correct pin code', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    const ok = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}/expenses`,
      payload: {pinCode: '1234'},
    });
    const forbidden = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}/expenses`,
      payload: {pinCode: '0000'},
    });

    expect(ok.statusCode).toBe(200);
    expect(ok.json()).toEqual([]);
    expect(forbidden.statusCode).toBe(403);
    expect(forbidden.json()).toMatchObject({code: 'B4003'});
  });

  it('persists the canonical reversal from the full request without rates', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});
    const [alice, bob] = event.users;
    if (alice === undefined || bob === undefined) throw new Error('Expected event participants');
    const original = new ExpenseValueObject({
      eventId: event.id,
      currencyId: eurId,
      description: 'Dinner',
      userWhoPaidId: alice.id,
      expenseType: ExpenseType.Expense,
      isCustomRate: false,
      splitInformation: [
        {userId: bob.id, amount: 10, exchangedAmount: 12.5},
        {userId: bob.id, amount: 10, exchangedAmount: 12.51},
      ],
    }).value;
    await testApp.rDataService.expense.insert(original);
    const createdAt = '2026-02-03T04:05:06.000Z';
    const response = await testApp.app.inject({
      method: 'POST',
      url: '/v2/user/event/' + event.id + '/expense',
      payload: {
        pinCode: '1234',
        description: 'Undo dinner',
        createdAt,
        revertsExpenseId: original.id,
        currencyId: 'missing',
        userWhoPaidId: 'wrong',
        expenseType: ExpenseType.Expense,
        isCustomRate: true,
        splitInformation: [{userId: 'wrong', amount: 999, exchangedAmount: -11}],
      },
    });
    expect(response.statusCode).toBe(201);
    const reversal = response.json<IExpense>();
    expect(reversal).toMatchObject({
      currencyId: original.currencyId,
      userWhoPaidId: original.userWhoPaidId,
      expenseType: ExpenseType.Refund,
      isCustomRate: false,
      description: 'Undo dinner',
      createdAt,
      revertsExpenseId: original.id,
      splitInformation: [
        {userId: bob.id, amount: -10, exchangedAmount: -12.5},
        {userId: bob.id, amount: -10, exchangedAmount: -12.51},
      ],
    });
    expect(reversal.id).not.toBe(original.id);
    const [stored] = await testApp.rDataService.expense.findById(reversal.id);
    expect(stored).toMatchObject({
      ...reversal,
      createdAt: new Date(createdAt),
      updatedAt: new Date(reversal.updatedAt),
    });
  });

  it.each([
    [{pinCode: '0000', revertsExpenseId: 'missing'}, 403, 'B4003'],
    [{revertsExpenseId: 'missing'}, 400, 'B4012'],
    [{revertsExpenseId: 'missing', replacesExpenseId: 'missing'}, 400, 'B4014'],
  ])('checks access and correction links for a reversal %j', async (input, statusCode, code) => {
    const event = await createEvent(testApp.app, {currencyId: usdId});
    const response = await testApp.app.inject({
      method: 'POST',
      url: '/v2/user/event/' + event.id + '/expense',
      payload: {
        pinCode: '1234',
        description: 'Undo',
        currencyId: usdId,
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Refund,
        splitInformation: [{userId: 'user-1', amount: -10}],
        ...input,
      },
    });
    expect(response.statusCode).toBe(statusCode);
    expect(response.json()).toMatchObject({code});
    const [expenses] = await testApp.rDataService.expense.findByEventId(event.id);
    expect(expenses).toEqual([]);
  });
});
