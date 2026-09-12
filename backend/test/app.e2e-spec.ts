import {UserV3Controller} from '#api/http/user/user-v3.controller';

import {CURRENCIES_LIST} from '../src/constants';
import {TestApp, createTestApp} from './support/test-app';

describe('application bootstrap', () => {
  let testApp: TestApp;

  beforeAll(async () => {
    testApp = await createTestApp();
  });

  afterAll(async () => {
    await testApp.close();
  });

  it('resolves every controller from the dependency graph', () => {
    expect(testApp.app.get(UserV3Controller)).toBeInstanceOf(UserV3Controller);
  });

  it('reports the database as up on GET /health', async () => {
    const response = await testApp.app.inject({method: 'GET', url: '/health'});

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({
      status: 'ok',
      info: {database: {status: 'up'}},
      error: {},
      details: {database: {status: 'up'}},
    });
  });

  it('serves the generated OpenAPI document', async () => {
    const response = await testApp.app.inject({method: 'GET', url: '/swagger/api-json'});

    expect(response.statusCode).toBe(200);
    expect(response.json<{paths: Record<string, unknown>}>().paths).toHaveProperty('/user/event');
  });

  it('seeds every supported currency on startup', async () => {
    await testApp.reset();

    const [currencies] = await testApp.rDataService.currency.findAllSupported({orderBy: 'code'});

    expect(currencies.map((currency) => currency.code)).toEqual(CURRENCIES_LIST.map(({code}) => code).sort());
  });
});
