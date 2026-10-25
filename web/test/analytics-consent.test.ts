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
  assert.equal(consent.getConsent(), 'granted');
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

function collection(storage: () => Pick<Storage, 'getItem' | 'setItem'>) {
  const calls: unknown[][] = [];
  let loads = 0;
  let reloads = 0;
  let resolve!: () => void;
  const loaded = new Promise<void>((done) => {
    resolve = done;
  });
  const service = new AnalyticsService(
    {
      production: true,
      counterId: '123',
      hostname: 'commonex.ru',
      allowedHosts: 'commonex.ru',
      origin: 'https://commonex.ru',
      storage,
    },
    {
      load: () => {
        loads++;
        return loaded;
      },
      send: (...args) => {
        calls.push(args);
      },
      now: Date.now,
      reload: () => {
        reloads++;
      },
    },
  );
  return {service, calls, resolve, loads: () => loads, reloads: () => reloads};
}

test('new visits start analytics by default without saving an explicit choice', async () => {
  const values = new Map<string, string>();
  const s = collection(() => ({
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => {
      values.set(key, value);
    },
  }));
  s.service.navigate('home', 'https://commonex.ru/', '');
  assert.equal(s.loads(), 1);
  s.resolve();
  await Promise.resolve();
  assert.deepEqual(
    s.calls.map((call) => call[1]),
    ['init', 'hit'],
  );
  assert.equal(values.size, 0);
});

test('disabling default analytics stops a pending load and preserves refusal after reload', async () => {
  const values = new Map<string, string>();
  const storage = () => ({
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => {
      values.set(key, value);
    },
  });
  const s = collection(storage);
  s.service.navigate('home', 'https://commonex.ru/', '');
  assert.equal(s.loads(), 1);
  s.service.changeConsent('denied');
  s.resolve();
  await Promise.resolve();
  assert.deepEqual(s.calls, []);
  assert.equal(s.reloads(), 1);
  const nextVisit = collection(storage);
  nextVisit.service.navigate('home', 'https://commonex.ru/', '');
  assert.equal(nextVisit.loads(), 0);
  assert.equal(nextVisit.service.getConsent(), 'denied');
});
