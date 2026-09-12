import {CurrencyRateServiceAbstract} from '#domain/abstracts/currency-rate-service/currency-rate-service';

import {env} from '../../src/config';
import {TestApp, createTestApp} from '../support/test-app';

describe('HTTP /devtools', () => {
  let testApp: TestApp;
  const getCurrencyRate = jest.fn<Promise<Record<string, number> | null>, [string]>();

  beforeAll(async () => {
    testApp = await createTestApp({
      configure: (builder) => builder.overrideProvider(CurrencyRateServiceAbstract).useValue({getCurrencyRate}),
    });
  });

  afterAll(async () => {
    await testApp.close();
  });

  beforeEach(async () => {
    await testApp.reset();
  });

  it('rejects requests without the secret header with 401', async () => {
    const response = await testApp.app.inject({method: 'GET', url: '/devtools/currency-rate?date=2026-01-06'});

    expect(response.statusCode).toBe(401);
  });

  it('rejects a wrong secret with 401', async () => {
    const response = await testApp.app.inject({
      method: 'GET',
      url: '/devtools/currency-rate?date=2026-01-06',
      headers: {'x-devtools-secret': `${env.DEVTOOLS_SECRET}-wrong`},
    });

    expect(response.statusCode).toBe(401);
  });

  it('returns null when no rate is stored for the date', async () => {
    const response = await testApp.app.inject({
      method: 'GET',
      url: '/devtools/currency-rate?date=2026-01-06',
      headers: {'x-devtools-secret': env.DEVTOOLS_SECRET},
    });

    expect(response.statusCode).toBe(200);
    expect(response.body).toBe('');
  });

  it('fetches, stores and returns the rate for a date', async () => {
    getCurrencyRate.mockResolvedValue({USD: 1, EUR: 0.9});

    const fetchResponse = await testApp.app.inject({
      method: 'POST',
      url: '/devtools/currency-rate/fetch?date=2026-01-06',
      headers: {'x-devtools-secret': env.DEVTOOLS_SECRET},
    });
    const [stored] = await testApp.rDataService.currencyRate.findByDate('2026-01-06');

    expect(getCurrencyRate).toHaveBeenCalledWith('2026-01-06');
    expect(fetchResponse.statusCode).toBe(200);
    expect(fetchResponse.json()).toMatchObject({date: '2026-01-06', rate: {USD: 1, EUR: 0.9}});
    expect(stored?.rate).toEqual({USD: 1, EUR: 0.9});
  });
});
