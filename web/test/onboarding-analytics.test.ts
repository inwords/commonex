import type {AnalyticsEvent} from '../src/6-shared/analytics/analytics-event.ts';
import {safeEventParams} from '../src/6-shared/analytics/analytics-event.ts';
import {OnboardingService} from '../src/6-shared/services/onboarding-service.ts';
import assert from 'node:assert/strict';
import test from 'node:test';

function setup(initial?: string) {
  const events: AnalyticsEvent[] = [];
  const service = new OnboardingService(
    () => ({getItem: () => initial ?? null, setItem: () => {}}),
    (event) => events.push(event),
  );
  return {service, events};
}

test('starts on the first shown hint and ignores repeated renders of the same hint', () => {
  const {service, events} = setup();
  assert.deepEqual(events, []);
  service.stepShown('entry');
  service.stepShown('entry');
  service.stepShown('createEvent');
  service.stepShown('amount');
  service.stepShown('owner');
  assert.deepEqual(events, [
    {name: 'onboarding_started', params: {source: 'automatic'}},
    {name: 'onboarding_step_viewed', params: {step: 'entry'}},
    {name: 'onboarding_step_viewed', params: {step: 'createEvent'}},
    {name: 'onboarding_step_viewed', params: {step: 'amount'}},
    {name: 'onboarding_step_viewed', params: {step: 'owner'}},
  ]);
});

test('skip reports the last hint once and suppresses further hint views', () => {
  const {service, events} = setup();
  service.stepShown('split');
  service.skip();
  service.skip();
  service.stepShown('save');
  assert.deepEqual(events.slice(2), [{name: 'onboarding_skipped', params: {step: 'split'}}]);
  assert.equal(service.getSnapshot().status, 'skipped');
});

test('manual restart waits for a visible hint before starting a new run', () => {
  const {service, events} = setup();
  service.stepShown('entry');
  service.skip();
  service.restart();
  assert.equal(events.length, 3);
  service.stepShown('entry');
  service.stepShown('entry');
  assert.deepEqual(events.slice(3), [
    {name: 'onboarding_started', params: {source: 'manual'}},
    {name: 'onboarding_step_viewed', params: {step: 'entry'}},
  ]);
});

test('completion is sent once, only after a saved expense in the matching trip', () => {
  const {service, events} = setup();
  service.stepShown('save');
  service.complete('private-trip');
  service.expenseSaved('private-trip');
  service.complete('other-trip');
  assert.equal(events.length, 2);
  service.complete('private-trip');
  service.complete('private-trip');
  service.stepShown('debts');
  assert.deepEqual(events.slice(2), [{name: 'onboarding_completed'}]);
  assert.equal(JSON.stringify(events).includes('private-trip'), false);
});

test('persisted skipped and completed guides produce no new analytics', () => {
  for (const status of ['skipped', 'completed']) {
    const {service, events} = setup(JSON.stringify({status, expenseEventId: null}));
    service.stepShown('entry');
    service.skip();
    service.complete('trip');
    assert.deepEqual(events, []);
  }
});

test('onboarding parameters allow only fixed hint names and restart sources', () => {
  assert.deepEqual(safeEventParams({name: 'onboarding_step_viewed', params: {step: 'amount'}}), {
    schema_version: 1,
    platform: 'web',
    step: 'amount',
  });
  assert.equal(safeEventParams({name: 'onboarding_step_viewed', params: {step: 'private-trip'}} as never), null);
  assert.equal(safeEventParams({name: 'onboarding_started', params: {source: 'private-user'}} as never), null);
});
