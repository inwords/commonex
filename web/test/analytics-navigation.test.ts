import assert from 'node:assert/strict';
import test from 'node:test';
import {AnalyticsService} from '../src/6-shared/analytics/analytics-service.ts';

function setup() {
  const calls: unknown[][] = [];
  let consent = 'unknown';
  const service = new AnalyticsService(
    {
      production: true,
      counterId: '123',
      hostname: 'commonex.ru',
      allowedHosts: 'commonex.ru',
      origin: 'https://commonex.ru',
      consent: () => consent,
    },
    {
      load: async () => {},
      send: (...args) => {
        calls.push(args);
      },
      now: Date.now,
    },
  );
  return {
    service,
    calls,
    allow: () => {
      consent = 'granted';
    },
  };
}
test('retains landing campaign through consent delay and ignores repeated router effects', async () => {
  const {service, calls, allow} = setup();
  service.navigate('first', 'https://commonex.ru/?utm_source=telegram&utm_medium=messenger', 'https://t.me/private');
  assert.equal(calls.length, 0);
  allow();
  service.navigate('second', 'https://commonex.ru/event/ID?token=SECRET', '');
  service.navigate('second', 'https://commonex.ru/event/ID?token=SECRET', '');
  await Promise.resolve();
  const hits = calls.filter((call) => call[1] === 'hit');
  assert.equal(hits.length, 2);
  assert.equal(String(hits[0][2]).includes('utm_source=telegram'), true);
  assert.equal(hits[1][2], 'https://commonex.ru/event');
  assert.equal(JSON.stringify(calls).includes('SECRET'), false);
});
test('counts back navigation and different events even when canonical route is identical', async () => {
  const {service, calls, allow} = setup();
  allow();
  for (const [key, url] of [
    ['one', '/event/ONE'],
    ['two', '/event/TWO'],
    ['one', '/event/ONE'],
  ]) {
    service.navigate(key, `https://commonex.ru${url}`, '');
  }
  await Promise.resolve();
  assert.equal(calls.filter((call) => call[1] === 'hit').length, 3);
});
