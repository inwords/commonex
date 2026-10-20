import {CurrencyCode} from '#domain/entities/currency.entity';
import {ExpenseType} from '#domain/entities/expense.entity';

import {createEvent, findCurrencyIdByCode} from '../support/fixtures';
import {TestApp, createTestApp} from '../support/test-app';

describe('HTTP /v4/user expenses', () => {
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

  it('creates version one and returns the complete revision history', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});
    const [alice] = event.users;
    const createResponse = await testApp.app.inject({
      method: 'POST',
      url: `/v4/user/event/${event.id}/expense`,
      headers: {'idempotency-key': 'v4-create-expense-1'},
      payload: {
        pinCode: '1234',
        description: 'Dinner',
        userWhoPaidId: alice?.id,
        currencyId: eurId,
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: alice?.id, amount: 40, exchangedAmount: 50}],
      },
    });
    const created = createResponse.json<{
      id: string;
      version: number;
      createdAt: string;
      splitInformation: unknown[];
    }>();

    expect(createResponse.statusCode).toBe(201);
    expect(created).toMatchObject({version: 1, description: 'Dinner'});

    const revisionResponse = await testApp.app.inject({
      method: 'POST',
      url: `/v4/user/event/${event.id}/expense/${created.id}/revisions`,
      headers: {'idempotency-key': 'v4-revise-expense-1'},
      payload: {
        pinCode: '1234',
        expectedVersion: 1,
        description: 'Dinner corrected',
        userWhoPaidId: alice?.id,
        currencyId: eurId,
        createdAt: created.createdAt,
        splitInformation: [{userId: alice?.id, amount: 40, exchangedAmount: 50}],
      },
    });
    expect(revisionResponse.statusCode).toBe(201);
    expect(revisionResponse.json()).toMatchObject({id: created.id, version: 2, description: 'Dinner corrected'});

    const historyResponse = await testApp.app.inject({
      method: 'POST',
      url: `/v4/user/event/${event.id}/expense/${created.id}/history`,
      payload: {pinCode: '1234'},
    });
    expect(historyResponse.statusCode).toBe(200);
    expect(historyResponse.json()).toMatchObject([
      {id: created.id, version: 1, description: 'Dinner'},
      {id: created.id, version: 2, description: 'Dinner corrected'},
    ]);

    const latestResponse = await testApp.app.inject({
      method: 'POST',
      url: `/v4/user/event/${event.id}/expenses`,
      payload: {pinCode: '1234'},
    });
    expect(latestResponse.json()).toMatchObject([{id: created.id, version: 2, description: 'Dinner corrected'}]);
  });

  it('rejects unsupported currencies even when custom exchanged amounts are supplied', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});
    const [alice] = event.users;
    const response = await testApp.app.inject({
      method: 'POST',
      url: `/v4/user/event/${event.id}/expense`,
      payload: {
        pinCode: '1234',
        description: 'Unknown currency',
        userWhoPaidId: alice?.id,
        currencyId: 'unsupported-currency',
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: alice?.id, amount: 40, exchangedAmount: 50}],
      },
    });

    expect(response.statusCode).toBe(404);
    expect(response.json()).toMatchObject({code: 'B4004'});
  });

  it('rejects null optional expense fields without inserting expenses or revisions', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});
    const [alice] = event.users;
    const createExpense: (extra?: object) => ReturnType<typeof testApp.app.inject> = (extra = {}) =>
      testApp.app.inject({
        method: 'POST',
        url: `/v4/user/event/${event.id}/expense`,
        payload: {
          pinCode: '1234',
          description: 'Null validation',
          userWhoPaidId: alice?.id,
          currencyId: usdId,
          expenseType: ExpenseType.Expense,
          splitInformation: [{userId: alice?.id, amount: 40}],
          ...extra,
        },
      });

    const nullExchangedAmount = await createExpense({
      splitInformation: [{userId: alice?.id, amount: 40, exchangedAmount: null}],
    });
    const nullCreatedAt = await createExpense({createdAt: null});
    const [expensesAfterInvalidCreates] = await testApp.rDataService.expense.findAll({limit: 100});

    expect(nullExchangedAmount.statusCode).toBe(400);
    expect(nullCreatedAt.statusCode).toBe(400);
    expect(expensesAfterInvalidCreates).toHaveLength(0);

    const created = await createExpense();
    const expense = created.json<{id: string; createdAt: string}>();
    const revise: (extra: object) => ReturnType<typeof testApp.app.inject> = (extra) =>
      testApp.app.inject({
        method: 'POST',
        url: `/v4/user/event/${event.id}/expense/${expense.id}/revisions`,
        payload: {
          pinCode: '1234',
          expectedVersion: 1,
          description: 'Null validation revised',
          userWhoPaidId: alice?.id,
          currencyId: usdId,
          createdAt: expense.createdAt,
          splitInformation: [{userId: alice?.id, amount: 40}],
          ...extra,
        },
      });

    const nullRevisionAmount = await revise({
      splitInformation: [{userId: alice?.id, amount: 40, exchangedAmount: null}],
    });
    const nullRevisionCreatedAt = await revise({createdAt: null});
    const nullCustomRate = await revise({isCustomRate: null});
    const [history] = await testApp.rDataService.expense.findHistoryById(event.id, expense.id);

    expect(nullRevisionAmount.statusCode).toBe(400);
    expect(nullRevisionCreatedAt.statusCode).toBe(400);
    expect(nullCustomRate.statusCode).toBe(400);
    expect(history).toHaveLength(1);
  });

  it('returns the current snapshot for a stale version and rejects immutable fields and duplicate participants', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});
    const [alice, bob] = event.users;
    const created = await testApp.app.inject({
      method: 'POST',
      url: `/v4/user/event/${event.id}/expense`,
      payload: {
        pinCode: '1234',
        description: 'Lunch',
        userWhoPaidId: alice?.id,
        currencyId: usdId,
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: alice?.id, amount: 40}],
      },
    });
    const expense = created.json<{id: string; createdAt: string}>();
    const revise: (
      description: string,
      expectedVersion: number,
      extra?: object,
    ) => ReturnType<typeof testApp.app.inject> = (description, expectedVersion, extra = {}) =>
      testApp.app.inject({
        method: 'POST',
        url: `/v4/user/event/${event.id}/expense/${expense.id}/revisions`,
        headers: {'idempotency-key': `revise-${description}`},
        payload: {
          pinCode: '1234',
          expectedVersion,
          description,
          userWhoPaidId: alice?.id,
          currencyId: usdId,
          createdAt: expense.createdAt,
          splitInformation: [{userId: alice?.id, amount: 40}],
          ...extra,
        },
      });

    const first = await revise('Updated lunch', 1);
    expect(first.statusCode).toBe(201);
    const stale = await revise('Stale lunch', 1);
    expect(stale.statusCode).toBe(409);
    expect(stale.json()).toMatchObject({
      code: 'B4017',
      expense: {id: expense.id, version: 2, description: 'Updated lunch'},
    });

    const invalidMembers = await revise('Duplicate', 2, {
      splitInformation: [
        {userId: alice?.id, amount: 20},
        {userId: alice?.id, amount: 20},
      ],
    });
    expect(invalidMembers.statusCode).toBe(400);

    const immutableType = await revise('Changed type', 2, {expenseType: ExpenseType.Refund});
    expect(immutableType.statusCode).toBe(400);

    const validOtherMember = await revise('Bob pays', 2, {userWhoPaidId: bob?.id});
    expect(validOtherMember.statusCode).toBe(201);
  });
});
