import './helpers/register-source.ts';
import assert from 'node:assert/strict';
import test from 'node:test';
const {EventService} = await import('../src/5-entities/event/services/event-service.ts');
const {ExpenseService} = await import('../src/5-entities/expense/services/expense-service.ts');
const {httpClient} = await import('../src/6-shared/api/http-client.ts');
const {eventStore} = await import('../src/5-entities/event/stores/event-store.ts');
const {userStore} = await import('../src/5-entities/user/stores/user-store.ts');
const {expenseStore} = await import('../src/5-entities/expense/stores/expense-store.ts');
import type {AnalyticsEvent} from '../src/6-shared/analytics/analytics-event.ts';
const trip = {
  id: 'PRIVATE',
  name: 'PRIVATE_NAME',
  currencyId: 'rub',
  pinCode: 'SECRET_PIN',
  users: [{id: 'ACTOR', name: 'NAME', eventId: 'PRIVATE'}],
};
const draft = {name: trip.name, currencyId: trip.currencyId, pinCode: trip.pinCode, users: trip.users};

test('create reports once after retries and never invents a join', async () => {
  let attempts = 0;
  httpClient.request = async () => {
    if (++attempts < 3) throw {statusCode: 500};
    return trip;
  };
  const events: AnalyticsEvent[] = [];
  await new EventService((event) => events.push(event)).createEvent(draft);
  assert.equal(attempts, 3);
  assert.deepEqual(events, [{name: 'trip_created'}]);
  assert.equal(eventStore.entryMethod, 'created');
});
test('join propagates original access error and classifies it without exposing message', async () => {
  const error = {statusCode: 403, code: 'B4008', message: 'SECRET_TOKEN'};
  httpClient.request = async () => {
    throw error;
  };
  const events: AnalyticsEvent[] = [];
  await assert.rejects(
    new EventService((e) => events.push(e)).getEventInfo('PRIVATE', {token: 'SECRET'}, 'token'),
    (e) => e === error,
  );
  assert.deepEqual(events, [
    {name: 'join_started', params: {entry_method: 'token'}},
    {name: 'join_failed', params: {entry_method: 'token', reason: 'access_denied'}},
  ]);
});
test('manual access succeeds and a broken analytics callback cannot break creation', async () => {
  httpClient.request = async () => trip;
  const events: AnalyticsEvent[] = [];
  await new EventService((e) => events.push(e)).getEventInfo('PRIVATE', {pinCode: 'SECRET'}, 'manual');
  assert.deepEqual(
    events.map((e) => e.name),
    ['join_started', 'join_succeeded'],
  );
  assert.equal(eventStore.entryMethod, 'manual');
  assert.equal(
    await new EventService(() => {
      throw new Error('sdk');
    }).createEvent(draft),
    trip.id,
  );
});
test('expense and refund outcomes are separate and failed retries produce one failure', async () => {
  eventStore.setCurrentEvent(trip);
  userStore.setUsers(trip.users);
  const events: AnalyticsEvent[] = [];
  const service = new ExpenseService((e) => events.push(e));
  httpClient.request = async () => ({id: 'EXPENSE', expenseType: 'expense', splitInformation: []});
  await service.createExpense(
    {
      name: 'PRIVATE',
      amount: 100,
      currencyId: 'rub',
      userWhoPaidId: 'ACTOR',
      splitOption: '1',
      splitInformation: [],
    } as never,
    trip.id,
    trip.pinCode,
  );
  await service.createExpenseRefund(
    {
      name: 'PRIVATE',
      amount: 100,
      eventId: trip.id,
      currencyId: 'rub',
      userWhoPaidId: 'ACTOR',
      userWhoReceiveId: 'OTHER',
    } as never,
    trip.pinCode,
  );
  let attempts = 0;
  const error = {statusCode: 503, message: 'SECRET'};
  httpClient.request = async () => {
    attempts++;
    throw error;
  };
  await assert.rejects(
    service.createExpense({amount: 1, currencyId: 'rub', splitInformation: []} as never, trip.id, trip.pinCode),
    (e) => e === error,
  );
  assert.equal(attempts, 3);
  assert.deepEqual(events, [
    {name: 'expense_submitted'},
    {name: 'settlement_recorded'},
    {name: 'expense_failed', params: {reason: 'server'}},
  ]);
  assert.equal(expenseStore.isCreatingExpense, false);
});
