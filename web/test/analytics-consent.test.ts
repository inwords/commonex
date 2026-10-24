import assert from 'node:assert/strict';
import test from 'node:test';
import {AnalyticsConsent} from '../src/6-shared/analytics/analytics-consent.ts';
test('consent survives reload and can be revoked', () => {
  const data = new Map<string, string>();
  const storage = () => ({
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => {
      data.set(key, value);
    },
  });
  const consent = new AnalyticsConsent(storage);
  assert.equal(consent.getSnapshot(), 'unknown');
  let changes = 0;
  const unsubscribe = consent.subscribe(() => changes++);
  consent.set('granted');
  assert.equal(new AnalyticsConsent(storage).getSnapshot(), 'granted');
  consent.set('denied');
  assert.equal(new AnalyticsConsent(storage).getSnapshot(), 'denied');
  assert.equal(changes, 2);
  unsubscribe();
});
test('unavailable or malformed storage never grants analytics implicitly', () => {
  const consent = new AnalyticsConsent(() => {
    throw new Error('disabled');
  });
  assert.equal(consent.getSnapshot(), 'unknown');
  assert.doesNotThrow(() => consent.set('denied'));
  assert.equal(consent.getSnapshot(), 'denied');
  assert.equal(new AnalyticsConsent(() => ({getItem: () => 'true', setItem() {}})).getSnapshot(), 'unknown');
});
