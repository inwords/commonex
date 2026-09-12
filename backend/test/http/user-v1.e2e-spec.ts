import {CurrencyCode} from '#domain/entities/currency.entity';
import {ExpenseType} from '#domain/entities/expense.entity';

import {createEvent, findCurrencyIdByCode} from '../support/fixtures';
import {TestApp, createTestApp} from '../support/test-app';

describe('HTTP /user (v1)', () => {
  let testApp: TestApp;
  let usdId: string;

  beforeAll(async () => {
    testApp = await createTestApp();
  });

  afterAll(async () => {
    await testApp.close();
  });

  beforeEach(async () => {
    await testApp.reset();
    usdId = await findCurrencyIdByCode(testApp.rDataService, CurrencyCode.USD);
  });

  it('creates an event with its users', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    expect(event).toMatchObject({name: 'Trip', currencyId: usdId, pinCode: '1234', deletedAt: null});
    expect(event.users.map((user) => user.name)).toEqual(['Alice', 'Bob']);
    expect(event.users.every((user) => user.eventId === event.id)).toBe(true);
  });

  it('rejects an event with an unknown currency with 404 B4004', async () => {
    const response = await testApp.app.inject({
      method: 'POST',
      url: '/user/event',
      payload: {name: 'Trip', currencyId: 'missing', pinCode: '1234', users: []},
    });

    expect(response.statusCode).toBe(404);
    expect(response.json()).toEqual({statusCode: 404, code: 'B4004', message: 'Currency not found'});
  });

  it('returns event info for the correct pin code', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    const response = await testApp.app.inject({method: 'GET', url: `/user/event/${event.id}?pinCode=1234`});

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({id: event.id, name: 'Trip'});
  });

  it('rejects event info for a wrong pin code with 403 B4003', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    const response = await testApp.app.inject({method: 'GET', url: `/user/event/${event.id}?pinCode=9999`});

    expect(response.statusCode).toBe(403);
    expect(response.json()).toEqual({statusCode: 403, code: 'B4003', message: 'Invalid pin code'});
  });

  it('returns 404 B4001 for an unknown event', async () => {
    const response = await testApp.app.inject({method: 'GET', url: '/user/event/missing?pinCode=1234'});

    expect(response.statusCode).toBe(404);
    expect(response.json()).toMatchObject({code: 'B4001'});
  });

  it('deletes an event and then reports it as gone with 410 B4002', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    const deleteResponse = await testApp.app.inject({
      method: 'DELETE',
      url: `/user/event/${event.id}`,
      payload: {pinCode: '1234'},
    });
    const infoResponse = await testApp.app.inject({method: 'GET', url: `/user/event/${event.id}?pinCode=1234`});

    expect(deleteResponse.statusCode).toBe(200);
    expect(deleteResponse.json()).toMatchObject({id: event.id});
    expect(infoResponse.statusCode).toBe(410);
    expect(infoResponse.json()).toMatchObject({code: 'B4002'});
  });

  it('adds users to an event', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId, users: []});

    const response = await testApp.app.inject({
      method: 'POST',
      url: `/user/event/${event.id}/users`,
      payload: {pinCode: '1234', users: [{name: 'Carol'}]},
    });

    expect(response.statusCode).toBe(201);
    expect(response.json()).toEqual([expect.objectContaining({name: 'Carol', eventId: event.id})]);
  });

  it('creates an expense in the event currency and lists it', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});
    const [alice, bob] = event.users;

    const createResponse = await testApp.app.inject({
      method: 'POST',
      url: `/user/event/${event.id}/expense`,
      payload: {
        description: 'Lunch',
        userWhoPaidId: alice?.id,
        currencyId: usdId,
        expenseType: ExpenseType.Expense,
        splitInformation: [
          {userId: alice?.id, amount: 40},
          {userId: bob?.id, amount: 60},
        ],
      },
    });
    const listResponse = await testApp.app.inject({method: 'GET', url: `/user/event/${event.id}/expenses`});

    expect(createResponse.statusCode).toBe(201);
    expect(createResponse.json()).toMatchObject({
      eventId: event.id,
      splitInformation: [
        {userId: alice?.id, amount: 40, exchangedAmount: 40},
        {userId: bob?.id, amount: 60, exchangedAmount: 60},
      ],
    });
    expect(listResponse.statusCode).toBe(200);
    expect(listResponse.json()).toHaveLength(1);
  });
});
