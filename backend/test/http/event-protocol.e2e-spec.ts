import {CurrencyCode} from '#domain/entities/currency.entity';

import {createEvent, findCurrencyIdByCode} from '../support/fixtures';
import {TestApp, createTestApp} from '../support/test-app';

const oldMobile = {'user-agent': 'CommonEx/123 (Android/r)'};
const newMobile = {...oldMobile, 'x-commonex-protocol-version': '2'};

describe('HTTP event protocol compatibility', () => {
  let testApp: TestApp;
  let currencyId: string;

  beforeAll(async () => {
    testApp = await createTestApp();
  });

  afterAll(async () => {
    await testApp.close();
  });

  beforeEach(async () => {
    await testApp.reset();
    currencyId = await findCurrencyIdByCode(testApp.rDataService, CurrencyCode.USD);
  });

  const createNewEvent = async (): Promise<{id: string; version: number; users: {id: string}[]}> => {
    const response = await testApp.app.inject({
      method: 'POST',
      url: '/user/event',
      payload: {name: 'New trip', currencyId, pinCode: '1234', users: [{name: 'Alice'}], version: 2},
    });
    expect(response.statusCode).toBe(201);
    return response.json<{id: string; version: number; users: {id: string}[]}>();
  };

  it('keeps events created without a protocol compatible with old mobile clients', async () => {
    const event = await createEvent(testApp.app, {currencyId});
    const response = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}`,
      headers: oldMobile,
      payload: {pinCode: '1234'},
    });
    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({id: event.id, version: 1});
  });

  it.each([
    ['Android', 'CommonEx/123 (Android/r)'],
    ['iOS', 'CommonEx/456 (iOS/d)'],
  ])('blocks old %s clients from new events', async (_platform, userAgent) => {
    const event = await createNewEvent();
    const response = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}`,
      headers: {'user-agent': userAgent},
      payload: {pinCode: '1234'},
    });
    expect(response.statusCode).toBe(403);
    expect(response.json()).toMatchObject({code: 'B4020', requiredProtocolVersion: 2});
  });

  it('permits new mobile clients and browsers including Android browsers', async () => {
    const event = await createNewEvent();
    for (const headers of [newMobile, {'user-agent': 'Mozilla/5.0 (Linux; Android 15) Chrome/130'}, {}]) {
      const response = await testApp.app.inject({
        method: 'POST',
        url: `/v2/user/event/${event.id}`,
        headers,
        payload: {pinCode: '1234'},
      });
      expect(response.statusCode).toBe(200);
      expect(response.json()).toMatchObject({id: event.id, version: 2});
    }
  });

  it('rejects creating a new-protocol event from an incompatible mobile client', async () => {
    const response = await testApp.app.inject({
      method: 'POST',
      url: '/user/event',
      headers: oldMobile,
      payload: {name: 'New trip', currencyId, pinCode: '1234', users: [{name: 'Alice'}], version: 2},
    });
    expect(response.statusCode).toBe(403);
    expect(response.json()).toMatchObject({code: 'B4020', requiredProtocolVersion: 2});
    const [events] = await testApp.rDataService.event.findAll({limit: 10});
    expect(events).toEqual([]);
  });

  it('rejects invalid event protocols instead of silently falling back to legacy', async () => {
    for (const version of [null, 0, 3, '2']) {
      const response = await testApp.app.inject({
        method: 'POST',
        url: '/user/event',
        payload: {name: 'Trip', currencyId, pinCode: '1234', users: [{name: 'Alice'}], version},
      });
      expect(response.statusCode).toBe(400);
    }
  });

  it('does not allow an invalid capability header to unlock new events', async () => {
    const event = await createNewEvent();
    for (const version of ['0', '-1', 'two', '2,1', '9007199254740992']) {
      const response = await testApp.app.inject({
        method: 'POST',
        url: `/v2/user/event/${event.id}`,
        headers: {...oldMobile, 'x-commonex-protocol-version': version},
        payload: {pinCode: '1234'},
      });
      expect(response.statusCode).toBe(403);
      expect(response.json()).toMatchObject({code: 'B4020'});
    }
  });

  it('protects legacy reads, expense APIs, participant changes, sharing and deletion', async () => {
    const event = await createNewEvent();
    const first = event.users[0];
    const expense = {
      description: 'Dinner',
      userWhoPaidId: first?.id,
      currencyId,
      expenseType: 'expense',
      splitInformation: [{userId: first?.id, amount: 10}],
    };
    const calls = [
      {method: 'GET' as const, url: `/user/event/${event.id}?pinCode=1234`},
      {method: 'GET' as const, url: `/user/event/${event.id}/expenses`},
      {method: 'POST' as const, url: `/v2/user/event/${event.id}/expenses`, payload: {pinCode: '1234'}},
      {method: 'POST' as const, url: `/v4/user/event/${event.id}/expenses`, payload: {pinCode: '1234'}},
      {method: 'POST' as const, url: `/user/event/${event.id}/users`, payload: {users: [{name: 'Bob'}]}},
      {
        method: 'POST' as const,
        url: `/v2/user/event/${event.id}/users`,
        payload: {pinCode: '1234', users: [{name: 'Bob'}]},
      },
      {method: 'POST' as const, url: `/user/event/${event.id}/expense`, payload: expense},
      {method: 'POST' as const, url: `/v2/user/event/${event.id}/expense`, payload: {...expense, pinCode: '1234'}},
      {method: 'POST' as const, url: `/v4/user/event/${event.id}/expense`, payload: {...expense, pinCode: '1234'}},
      {method: 'POST' as const, url: `/v2/user/event/${event.id}/share-token`, payload: {pinCode: '1234'}},
      {method: 'DELETE' as const, url: `/user/event/${event.id}`, payload: {pinCode: '1234'}},
    ];
    for (const call of calls) {
      const response = await testApp.app.inject({...call, headers: oldMobile});
      expect({url: call.url, status: response.statusCode}).toEqual({url: call.url, status: 403});
      expect(response.json()).toMatchObject({code: 'B4020'});
    }
    const [stored] = await testApp.rDataService.event.findById(event.id);
    const [expenses] = await testApp.rDataService.expense.findByEventId(event.id);
    expect(stored?.deletedAt).toBeNull();
    expect(expenses).toEqual([]);
  });

  it('protects token joins and expense update/history paths', async () => {
    const event = await createNewEvent();
    const tokenResponse = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}/share-token`,
      payload: {pinCode: '1234'},
    });
    const {token} = tokenResponse.json<{token: string}>();
    const joined = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}`,
      headers: oldMobile,
      payload: {token},
    });
    expect(joined.statusCode).toBe(403);
    for (const suffix of ['history', 'revisions']) {
      const response = await testApp.app.inject({
        method: 'POST',
        url: `/v4/user/event/${event.id}/expense/any-expense/${suffix}`,
        headers: oldMobile,
        payload: {pinCode: '1234'},
      });
      expect(response.statusCode).toBe(403);
    }
  });

  it('forbids editing legacy events for browsers and new mobile clients', async () => {
    const event = await createEvent(testApp.app, {currencyId});
    const expenseInput = {
      pinCode: '1234',
      description: 'Dinner',
      userWhoPaidId: event.users[0]?.id,
      currencyId,
      expenseType: 'expense',
      splitInformation: [{userId: event.users[0]?.id, amount: 10}],
    };
    const response = await testApp.app.inject({
      method: 'POST',
      url: `/v4/user/event/${event.id}/expense`,
      payload: expenseInput,
    });
    expect(response.statusCode).toBe(201);
    const expense = response.json<{id: string; createdAt: string}>();
    const updateInput = {
      pinCode: '1234',
      description: 'Corrected dinner',
      userWhoPaidId: event.users[0]?.id,
      currencyId,
      splitInformation: [{userId: event.users[0]?.id, amount: 10}],
    };
    for (const headers of [{}, newMobile]) {
      const updated = await testApp.app.inject({
        method: 'POST',
        url: `/v4/user/event/${event.id}/expense/${expense.id}/revisions`,
        headers,
        payload: {...updateInput, createdAt: expense.createdAt, expectedVersion: 1},
      });
      expect(updated.statusCode).toBe(403);
      expect(updated.json()).toMatchObject({code: 'B4021'});
    }
    const [history] = await testApp.rDataService.expense.findHistoryById(event.id, expense.id);
    expect(history).toHaveLength(1);
  });

  it('allows a compatible mobile client to create a new-protocol event', async () => {
    const response = await testApp.app.inject({
      method: 'POST',
      url: '/user/event',
      headers: newMobile,
      payload: {name: 'Trip', currencyId, pinCode: '1234', users: [{name: 'Alice'}], version: 2},
    });
    expect(response.statusCode).toBe(201);
    expect(response.json()).toMatchObject({version: 2});
  });
});
