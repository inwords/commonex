import {CurrencyCode} from '#domain/entities/currency.entity';

import {findCurrencyIdByCode} from '../support/fixtures';
import {TestApp, createTestApp} from '../support/test-app';

describe('HTTP idempotency-key header', () => {
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

  const payload = (): object => ({name: 'Trip', currencyId: usdId, pinCode: '1234', users: [{name: 'Alice'}]});

  it('replays the first response and creates nothing on a repeated key', async () => {
    const first = await testApp.app.inject({
      method: 'POST',
      url: '/user/event',
      headers: {'idempotency-key': 'key-1'},
      payload: payload(),
    });
    const second = await testApp.app.inject({
      method: 'POST',
      url: '/user/event',
      headers: {'idempotency-key': 'key-1'},
      payload: payload(),
    });
    const [events] = await testApp.rDataService.event.findAll({limit: 10});

    expect(first.statusCode).toBe(201);
    expect(second.statusCode).toBe(201);
    expect(second.json()).toEqual(first.json());
    expect(events).toHaveLength(1);
  });

  it('rejects a reused key with a different body with 422 B4011', async () => {
    await testApp.app.inject({
      method: 'POST',
      url: '/user/event',
      headers: {'idempotency-key': 'key-2'},
      payload: payload(),
    });

    const response = await testApp.app.inject({
      method: 'POST',
      url: '/user/event',
      headers: {'idempotency-key': 'key-2'},
      payload: {...payload(), name: 'Other'},
    });

    expect(response.statusCode).toBe(422);
    expect(response.json()).toMatchObject({code: 'B4011'});
  });

  it('treats the same key on a different route as a different request', async () => {
    const create = await testApp.app.inject({
      method: 'POST',
      url: '/user/event',
      headers: {'idempotency-key': 'key-3'},
      payload: payload(),
    });
    const {id} = create.json<{id: string}>();

    const addUsers = await testApp.app.inject({
      method: 'POST',
      url: `/user/event/${id}/users`,
      headers: {'idempotency-key': 'key-3'},
      payload: {pinCode: '1234', users: [{name: 'Bob'}]},
    });

    expect(addUsers.statusCode).toBe(422);
  });
});
