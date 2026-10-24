import assert from 'node:assert/strict';
import test from 'node:test';
import {AnalyticsNavigation} from '../src/6-shared/analytics/analytics-navigation.ts';
test('retains landing campaign through consent delay and ignores repeated router effects', () => {
  const calls: unknown[][] = [];
  const navigation = new AnalyticsNavigation(
    'https://commonex.ru/?utm_source=telegram&utm_medium=messenger',
    'https://t.me/private',
  );
  navigation.update('first', 'https://commonex.ru/', false, (...args) => calls.push(args));
  navigation.update('second', 'https://commonex.ru/event/ID?token=SECRET', true, (...args) => calls.push(args));
  navigation.update('second', 'https://commonex.ru/event/ID?token=SECRET', true, (...args) => calls.push(args));
  assert.equal(calls.length, 2);
  assert.equal(String(calls[0][0]).includes('utm_source=telegram'), true);
  assert.equal(calls[1][0], 'https://commonex.ru/event');
  assert.equal(JSON.stringify(calls).includes('SECRET'), false);
});
test('counts back navigation and different events even when canonical route is identical', () => {
  const calls: unknown[][] = [];
  const navigation = new AnalyticsNavigation('https://commonex.ru/event/ONE', '');
  for (const [key, url] of [
    ['one', '/event/ONE'],
    ['two', '/event/TWO'],
    ['one', '/event/ONE'],
  ])
    navigation.update(key, `https://commonex.ru${url}`, true, (...args) => calls.push(args));
  assert.equal(calls.length, 3);
});
