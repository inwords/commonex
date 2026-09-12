import {insertTodayRate} from '../support/fixtures';
import {TestApp, createTestApp} from '../support/test-app';

describe('HTTP /v3/user/currencies/all', () => {
  let testApp: TestApp;

  beforeAll(async () => {
    testApp = await createTestApp();
  });

  afterAll(async () => {
    await testApp.close();
  });

  beforeEach(async () => {
    await testApp.reset();
  });

  it('returns currencies with rates, a weak ETag and no-cache headers', async () => {
    await insertTodayRate(testApp.rDataService, {USD: 1, EUR: 0.92, XXX: 5});

    const response = await testApp.app.inject({method: 'GET', url: '/v3/user/currencies/all'});
    const body = response.json<{currencies: {code: string}[]; exchangeRate: Record<string, number>}>();

    expect(response.statusCode).toBe(200);
    expect(response.headers['cache-control']).toBe('private, no-cache');
    expect(response.headers.etag).toMatch(/^W\/"currencies-v3-[0-9a-f]{40}"$/);
    expect(body.currencies.map((currency) => currency.code)).toEqual(['AED', 'EUR', 'JPY', 'RUB', 'TRY', 'USD']);
    expect(body.exchangeRate).toEqual({USD: 1, EUR: 0.92});
  });

  it('returns 304 with the same ETag when If-None-Match matches', async () => {
    await insertTodayRate(testApp.rDataService, {USD: 1, EUR: 0.92});
    const first = await testApp.app.inject({method: 'GET', url: '/v3/user/currencies/all'});

    const second = await testApp.app.inject({
      method: 'GET',
      url: '/v3/user/currencies/all',
      headers: {'if-none-match': String(first.headers.etag)},
    });

    expect(second.statusCode).toBe(304);
    expect(second.headers.etag).toBe(first.headers.etag);
    expect(second.body).toBe('');
  });

  it('returns 404 B4005 when there is no rate for today', async () => {
    const response = await testApp.app.inject({method: 'GET', url: '/v3/user/currencies/all'});

    expect(response.statusCode).toBe(404);
    expect(response.json()).toMatchObject({code: 'B4005'});
  });
});
