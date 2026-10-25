import assert from 'node:assert/strict';
import test from 'node:test';
import {AnalyticsService} from '../src/6-shared/analytics/analytics-service.ts';
function createService(storage: () => Pick<Storage, 'getItem' | 'setItem'>) {
  return new AnalyticsService(
    {production: false, counterId: '', hostname: '', allowedHosts: '', origin: 'https://commonex.ru', storage},
    {load: async () => {}, send() {}, now: Date.now},
  );
}
test('consent survives reload and can be revoked', () => {
  const data = new Map<string, string>();
  const storage = () => ({
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => {
      data.set(key, value);
    },
  });
  const consent = createService(storage);
  assert.equal(consent.getConsent(), 'unknown');
  let changes = 0;
  const unsubscribe = consent.subscribeConsent(() => changes++);
  consent.changeConsent('granted');
  assert.equal(createService(storage).getConsent(), 'granted');
  consent.changeConsent('denied');
  assert.equal(createService(storage).getConsent(), 'denied');
  assert.equal(changes, 2);
  unsubscribe();
});
test('unavailable or malformed storage never grants analytics implicitly', () => {
  const consent = createService(() => {
    throw new Error('disabled');
  });
  assert.equal(consent.getConsent(), 'unknown');
  assert.doesNotThrow(() => consent.changeConsent('denied'));
  assert.equal(consent.getConsent(), 'denied');
  assert.equal(createService(() => ({getItem: () => 'true', setItem() {}})).getConsent(), 'unknown');
});
