import assert from 'node:assert/strict';
import test from 'node:test';
import {AnalyticsService} from '../src/6-shared/analytics/analytics-service.ts';
function setup(overrides = {}) {
  const calls: unknown[][] = [];
  let loads = 0;
  let resolve!: () => void;
  let reject!: () => void;
  let time = 0;
  const loaded = new Promise<void>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  const service = new AnalyticsService(
    {
      production: true,
      counterId: '123',
      hostname: 'commonex.ru',
      allowedHosts: 'commonex.ru',
      consent: () => 'granted',
      origin: 'https://commonex.ru',
      ...overrides,
    },
    {
      load: () => {
        loads++;
        return loaded;
      },
      send: (...args: unknown[]) => {
        calls.push(args);
      },
      now: () => time,
    },
  );
  return {
    service,
    calls,
    resolve,
    reject,
    loads: () => loads,
    advance: () => {
      time += 31000;
    },
  };
}
test('production, enabled analytics, valid ID and exact hostname are all required', () => {
  for (const config of [
    {production: false},
    {counterId: ''},
    {counterId: '0'},
    {counterId: 'abc'},
    {allowedHosts: ''},
    {hostname: 'commonex.ru.evil.test'},
    {consent: () => 'denied'},
    {consent: () => 'unknown'},
  ]) {
    const s = setup(config);
    s.service.initialize();
    s.service.pageview('/', '');
    s.service.track({name: 'trip_created'});
    assert.equal(s.loads(), 0);
    assert.deepEqual(s.calls, []);
  }
});
test('initializes once, sends safe pageview before goals and drops extra payload', async () => {
  const s = setup();
  s.service.initialize();
  s.service.initialize();
  s.service.track({name: 'trip_created', params: {token: 'SECRET'}} as never);
  s.service.pageview('/event/PRIVATE?token=SECRET', 'https://t.me/private');
  s.resolve();
  await Promise.resolve();
  assert.equal(s.loads(), 1);
  assert.deepEqual(
    s.calls.map((c) => c[1]),
    ['init', 'hit', 'reachGoal'],
  );
  assert.equal(s.calls[1][2], 'https://commonex.ru/event');
  assert.equal(JSON.stringify(s.calls).includes('SECRET'), false);
  assert.equal(JSON.stringify(s.calls).includes('PRIVATE'), false);
  assert.deepEqual(s.calls[2].slice(2), ['trip_created', {schema_version: 1, platform: 'web'}]);
});
test('bounded queue expires and stops after revocation even during script load', async () => {
  const s = setup();
  s.service.initialize();
  s.service.pageview('/', '');
  for (let i = 0; i < 100; i++) {
    s.service.track({name: 'trip_created'});
  }
  s.resolve();
  await Promise.resolve();
  assert.equal(s.calls.filter((c) => c[1] === 'reachGoal').length, 49);
  s.service.stop();
  s.service.track({name: 'trip_created'});
  assert.equal(s.calls.filter((c) => c[1] === 'reachGoal').length, 49);
  const stale = setup();
  stale.service.initialize();
  stale.service.pageview('/', '');
  stale.service.track({name: 'trip_created'});
  stale.advance();
  stale.resolve();
  await Promise.resolve();
  assert.equal(stale.calls.filter((c) => c[1] === 'reachGoal').length, 0);
  const revoked = setup();
  revoked.service.initialize();
  revoked.service.pageview('/', '');
  revoked.service.stop();
  revoked.resolve();
  await Promise.resolve();
  assert.deepEqual(revoked.calls, []);
});
test('blocked script or throwing SDK never breaks product actions', async () => {
  const blocked = setup();
  blocked.service.initialize();
  blocked.service.pageview('/', '');
  blocked.reject();
  await Promise.resolve();
  await Promise.resolve();
  assert.doesNotThrow(() => blocked.service.track({name: 'trip_created'}));
  const service = new AnalyticsService(
    {
      production: true,
      counterId: '1',
      hostname: 'a.test',
      allowedHosts: 'a.test',
      origin: 'https://a.test',
      consent: () => 'granted',
    },
    {
      load: async () => {},
      send: () => {
        throw new Error('sdk');
      },
      now: Date.now,
    },
  );
  assert.doesNotThrow(() => service.initialize());
  await Promise.resolve();
  assert.doesNotThrow(() => service.pageview('/', ''));
});
test('pageviews also obey queue bounds while the script is loading', async () => {
  const s = setup();
  s.service.initialize();
  for (let i = 0; i < 100; i++) {
    s.service.pageview('/support', '');
  }
  s.resolve();
  await Promise.resolve();
  assert.equal(s.calls.filter((c) => c[1] === 'hit').length, 50);
});
test('seeds SDK initialization with sanitized landing URL and referrer before technical requests', async () => {
  const s = setup();
  s.service.initialize();
  s.resolve();
  await Promise.resolve();
  assert.deepEqual(s.calls, []);
  s.service.pageview('/event/PRIVATE?token=SECRET&utm_source=telegram', 'https://t.me/SECRET');
  const init = s.calls[0][2] as {url: string; referrer: string; disableYtm: boolean};
  assert.equal(init.url, 'https://commonex.ru/event?utm_source=telegram');
  assert.equal(init.referrer, 'https://t.me/');
  assert.equal(init.disableYtm, true);
  assert.equal(JSON.stringify(s.calls).includes('SECRET'), false);
});

test('production website uses the public counter configuration without analytics environment variables', async () => {
  const previousEnvironment = process.env.NODE_ENV;
  const previousWindow = Object.getOwnPropertyDescriptor(globalThis, 'window');
  const calls: unknown[][] = [];
  try {
    Reflect.set(process.env, 'NODE_ENV', 'production');
    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      value: {location: {hostname: 'commonex.ru', origin: 'https://commonex.ru'}},
    });
    const service = new AnalyticsService(undefined, {
      load: async () => {},
      send: (...args) => {
        calls.push(args);
      },
      now: Date.now,
    });
    service.changeConsent('granted');
    service.navigate('home', 'https://commonex.ru/', '');
    await Promise.resolve();
    assert.equal(service.isConfigured(), true);
    assert.equal(calls[0][0], 113775783);
    assert.equal(calls[0][1], 'init');
    assert.equal((calls[0][2] as {webvisor: boolean}).webvisor, false);
  } finally {
    if (previousEnvironment === undefined) {
      Reflect.deleteProperty(process.env, 'NODE_ENV');
    } else {
      Reflect.set(process.env, 'NODE_ENV', previousEnvironment);
    }
    if (previousWindow) {
      Object.defineProperty(globalThis, 'window', previousWindow);
    } else {
      Reflect.deleteProperty(globalThis, 'window');
    }
  }
});
