import {IBanner} from '#domain/entities/banner.entity';

import {TestApp, createTestApp} from '../support/test-app';

const banner: IBanner = {
  id: 1,
  type: 'general_warning',
  text: 'Some features are temporarily unavailable.',
  platform: 'Android',
  minVersionCode: 0,
  maxVersionCode: 20,
  enabled: true,
};

describe('HTTP /user/banner', () => {
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

  it('returns only the banner type and text with six-hour cache headers', async () => {
    await testApp.rDataService.banner.insert(banner);

    const response = await testApp.app.inject({
      method: 'GET',
      url: '/user/banner',
      headers: {'user-agent': 'CommonEx/20 (Android/r)'},
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({type: banner.type, text: banner.text});
    expect(response.headers['cache-control']).toBe('public, max-age=21600');
    expect(response.headers.vary).toBe('User-Agent');
  });

  it('returns an empty cacheable response when no banner is configured', async () => {
    const response = await testApp.app.inject({
      method: 'GET',
      url: '/user/banner',
      headers: {'user-agent': 'CommonEx/20 (Android/r)'},
    });

    expect(response.statusCode).toBe(204);
    expect(response.body).toBe('');
    expect(response.headers['cache-control']).toBe('public, max-age=21600');
    expect(response.headers.vary).toBe('User-Agent');
  });

  it.each([undefined, 'CommonEx/not-a-version (Android/r)', 'Mozilla/5.0'])(
    'returns no banner for an unrecognized User-Agent %s',
    async (userAgent) => {
      await testApp.rDataService.banner.insert(banner);

      const response = await testApp.app.inject({
        method: 'GET',
        url: '/user/banner',
        headers: userAgent == null ? {} : {'user-agent': userAgent},
      });

      expect(response.statusCode).toBe(204);
      expect(response.body).toBe('');
      expect(response.headers['cache-control']).toBe('public, max-age=21600');
      expect(response.headers.vary).toBe('User-Agent');
    },
  );

  it('varies the response by the client platform and build number', async () => {
    const iosBanner: IBanner = {...banner, id: 2, platform: 'iOS', maxVersionCode: null, text: 'iOS warning'};
    await testApp.rDataService.banner.insert([banner, iosBanner]);

    for (const [userAgent, expected] of [
      ['CommonEx/20 (Android/r)', {type: banner.type, text: banner.text}],
      ['CommonEx/21 (Android/r)', null],
      ['CommonEx/21 (iOS/r)', {type: iosBanner.type, text: iosBanner.text}],
    ] as const) {
      const response = await testApp.app.inject({
        method: 'GET',
        url: '/user/banner',
        headers: {'user-agent': userAgent},
      });

      expect(response.statusCode).toBe(expected == null ? 204 : 200);
      if (expected == null) {
        expect(response.body).toBe('');
      } else {
        expect(response.json()).toEqual(expected);
      }
    }
  });

  it('prioritizes a required update over a newer matching warning', async () => {
    await testApp.rDataService.banner.insert([
      {...banner, type: 'update_required', text: 'Please update CommonEx.'},
      {...banner, id: 2},
    ]);

    const response = await testApp.app.inject({
      method: 'GET',
      url: '/user/banner',
      headers: {'user-agent': 'CommonEx/20 (Android/r)'},
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({type: 'update_required', text: 'Please update CommonEx.'});
  });

  it('reflects a direct PostgreSQL edit on the next server request', async () => {
    await testApp.rDataService.banner.insert(banner);
    const request = {method: 'GET', url: '/user/banner', headers: {'user-agent': 'CommonEx/20 (Android/r)'}} as const;
    const before = await testApp.app.inject(request);
    expect(before.json()).toEqual({type: banner.type, text: banner.text});

    await testApp.rDataService.dataSource.query('UPDATE banner SET text = $1 WHERE id = $2', [
      'Updated warning',
      banner.id,
    ]);

    const after = await testApp.app.inject(request);
    expect(after.statusCode).toBe(200);
    expect(after.json()).toEqual({type: banner.type, text: 'Updated warning'});
  });
});
