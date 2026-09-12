import {TestApp, createTestApp} from '../support/test-app';

describe('HTTP request validation', () => {
  let testApp: TestApp;

  beforeAll(async () => {
    testApp = await createTestApp();
  });

  afterAll(async () => {
    await testApp.close();
  });

  it('rejects unknown properties with 400 B4006', async () => {
    const response = await testApp.app.inject({
      method: 'POST',
      url: '/user/event',
      payload: {name: 'Trip', currencyId: 'x', pinCode: '1234', users: [], extra: true},
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toEqual({
      statusCode: 400,
      code: 'B4006',
      message: 'property extra should not exist',
    });
  });

  it('rejects a pin code that is not exactly four characters', async () => {
    const response = await testApp.app.inject({
      method: 'POST',
      url: '/user/event',
      payload: {name: 'Trip', currencyId: 'x', pinCode: '123', users: []},
    });

    expect(response.statusCode).toBe(400);
    expect(response.json<{message: string}>().message).toContain('pinCode');
  });

  it('joins several validation messages with a semicolon', async () => {
    const response = await testApp.app.inject({method: 'POST', url: '/user/event', payload: {}});

    expect(response.statusCode).toBe(400);
    expect(response.json<{message: string}>().message.split('; ').length).toBeGreaterThan(1);
  });

  it('requires either pinCode or token on v2 event info', async () => {
    const response = await testApp.app.inject({method: 'POST', url: '/v2/user/event/x', payload: {}});

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({code: 'B4006'});
  });
});
