import assert from 'node:assert/strict';
import test from 'node:test';
import {OnboardingService} from '../src/6-shared/services/onboarding-service.ts';

const setup = (initial?: string) => {
  const values = new Map<string, string>(initial ? [['commonex_guided_onboarding', initial]] : []);
  const storage = {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => {
      values.set(key, value);
    },
  };
  return {service: new OnboardingService(() => storage), storage};
};

test('skipping persists separately from completing and restart clears progress', () => {
  const {service, storage} = setup();
  service.skip();
  assert.equal(new OnboardingService(() => storage).getSnapshot().status, 'skipped');
  service.restart();
  assert.deepEqual(service.getSnapshot(), {status: 'active', expenseEventId: null});
});

test('finishing requires a saved expense in the current event', () => {
  const {service, storage} = setup();
  service.complete('trip');
  assert.equal(service.getSnapshot().status, 'active');
  service.expenseSaved('trip');
  service.complete('other-trip');
  assert.equal(service.getSnapshot().status, 'active');
  const resumed = new OnboardingService(() => storage);
  resumed.complete('trip');
  assert.equal(resumed.getSnapshot().status, 'completed');
});

test('saving after skip does not resume onboarding', () => {
  const {service} = setup();
  service.skip();
  service.expenseSaved('trip');
  assert.deepEqual(service.getSnapshot(), {status: 'skipped', expenseEventId: null});
});

test('malformed storage and unavailable storage do not prevent onboarding actions', () => {
  for (const initial of ['null', '{', '{"status":"unexpected"}', '{"status":"active","expenseEventId":42}']) {
    assert.deepEqual(setup(initial).service.getSnapshot(), {status: 'active', expenseEventId: null});
  }
  const service = new OnboardingService(() => {
    throw new Error('Storage disabled');
  });
  assert.doesNotThrow(() => service.skip());
  assert.equal(service.getSnapshot().status, 'skipped');
});

test('subscribers see saved progress and can unsubscribe', () => {
  const {service} = setup();
  const statuses: string[] = [];
  const unsubscribe = service.subscribe(() => statuses.push(service.getSnapshot().status));
  service.expenseSaved('trip');
  service.complete('trip');
  unsubscribe();
  service.restart();
  assert.deepEqual(statuses, ['active', 'completed']);
});
