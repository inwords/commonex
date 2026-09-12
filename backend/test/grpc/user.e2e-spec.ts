import {Metadata, status} from '@grpc/grpc-js';

import {CurrencyCode} from '#domain/entities/currency.entity';

import {createEvent, findCurrencyIdByCode} from '../support/fixtures';
import {UserServiceClient, callUnary, createUserServiceClient, expectGrpcError} from '../support/grpc-client';
import {TestApp, createTestApp} from '../support/test-app';

interface EventResponse {
  id: string;
  name: string;
  currencyId: string;
  pinCode: string;
  users: {id: string; name: string; eventId: string}[];
}

interface ExpenseResponse {
  id: string;
  expenseType: string;
  isCustomRate: boolean;
  createdAt: string;
  updatedAt: string;
  splitInformation: {userId: string; amount: number; exchangedAmount?: number}[];
}

describe('gRPC UserService', () => {
  let testApp: TestApp;
  let client: UserServiceClient;
  let usdId: string;

  beforeAll(async () => {
    testApp = await createTestApp({grpc: true});

    const {grpcUrl} = testApp;

    if (grpcUrl === null) {
      throw new Error('The test app did not start a gRPC microservice');
    }

    client = createUserServiceClient(grpcUrl);
  });

  afterAll(async () => {
    client.close();
    await testApp.close();
  });

  beforeEach(async () => {
    await testApp.reset();
    usdId = await findCurrencyIdByCode(testApp.rDataService, CurrencyCode.USD);
  });

  it('creates an event and returns its users with string event ids', async () => {
    const response = await callUnary<EventResponse>(client, 'CreateEvent', {
      name: 'Trip',
      currencyId: usdId,
      pinCode: '1234',
      users: [{name: 'Alice'}],
    });

    expect(response).toMatchObject({name: 'Trip', currencyId: usdId, pinCode: '1234'});
    expect(response.users).toEqual([expect.objectContaining({name: 'Alice', eventId: response.id})]);
  });

  it('maps a wrong pin code to PERMISSION_DENIED with the domain error code in metadata', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    const error = await expectGrpcError(callUnary(client, 'GetEventInfo', {eventId: event.id, pinCode: '0000'}));

    expect(error).toEqual({code: status.PERMISSION_DENIED, details: 'Invalid pin code', errorCode: 'B4003'});
  });

  it('maps an unknown event to NOT_FOUND', async () => {
    const error = await expectGrpcError(callUnary(client, 'GetEventInfo', {eventId: 'missing', pinCode: '1234'}));

    expect(error).toEqual({code: status.NOT_FOUND, details: 'Event not found', errorCode: 'B4001'});
  });

  it('deletes an event and then reports it as deleted', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    const response = await callUnary<{id: string; deletedAt: string}>(client, 'DeleteEvent', {
      eventId: event.id,
      pinCode: '1234',
    });

    expect(response.id).toBe(event.id);
    expect(new Date(response.deletedAt).toISOString()).toBe(response.deletedAt);

    const error = await expectGrpcError(callUnary(client, 'GetEventInfo', {eventId: event.id, pinCode: '1234'}));

    expect(error).toEqual({code: status.FAILED_PRECONDITION, details: 'Event is deleted', errorCode: 'B4002'});
  });

  it('validates the request and maps violations to INVALID_ARGUMENT', async () => {
    const error = await expectGrpcError(
      callUnary(client, 'CreateEvent', {name: 'Trip', currencyId: usdId, pinCode: '12', users: []}),
    );

    expect(error.code).toBe(status.INVALID_ARGUMENT);
    expect(error.details).toContain('pinCode');
    expect(error.errorCode).toBe('B4006');
  });

  it('round-trips fractional amounts, enum strings and ISO timestamps on CreateExpenseV2', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});
    const [alice] = event.users;

    const response = await callUnary<ExpenseResponse>(client, 'CreateExpenseV2', {
      eventId: event.id,
      pinCode: '1234',
      description: 'Lunch',
      userWhoPaidId: alice?.id,
      currencyId: usdId,
      expenseType: 'expense',
      splitInformation: [{userId: alice?.id, amount: 40.5}],
    });
    const [stored] = await testApp.rDataService.expense.findByEventId(event.id);

    expect(response.expenseType).toBe('expense');
    expect(response.isCustomRate).toBe(false);
    expect(response.splitInformation).toEqual([{userId: alice?.id, amount: 40.5, exchangedAmount: 40.5}]);
    expect(response.createdAt).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
    expect(stored[0]?.expenseType).toBe('expense');
  });

  it('returns a share token with an ISO expiry on CreateEventShareTokenV2', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    const response = await callUnary<{token: string; expiresAt: string}>(client, 'CreateEventShareTokenV2', {
      eventId: event.id,
      pinCode: '1234',
    });

    expect(response.token).toMatch(/^[0-9a-f]{64}$/);
    expect(new Date(response.expiresAt).toISOString()).toBe(response.expiresAt);
  });

  it('replays CreateEvent when the idempotency-key metadata repeats', async () => {
    const metadata = new Metadata();
    metadata.set('idempotency-key', 'grpc-key-1');
    const request = {name: 'Trip', currencyId: usdId, pinCode: '1234', users: [{name: 'Alice'}]};

    const first = await callUnary<EventResponse>(client, 'CreateEvent', request, metadata);
    const second = await callUnary<EventResponse>(client, 'CreateEvent', request, metadata);
    const [events] = await testApp.rDataService.event.findAll({limit: 10});

    expect(second.id).toBe(first.id);
    expect(events).toHaveLength(1);
  });

  it('replays CreateExpenseV2 when the idempotency-key metadata repeats', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});
    const [alice] = event.users;
    const metadata = new Metadata();
    metadata.set('idempotency-key', 'grpc-key-2');
    const request = {
      eventId: event.id,
      pinCode: '1234',
      description: 'Coffee',
      userWhoPaidId: alice?.id,
      currencyId: usdId,
      expenseType: 'expense',
      splitInformation: [{userId: alice?.id, amount: 10}],
    };

    const first = await callUnary<ExpenseResponse>(client, 'CreateExpenseV2', request, metadata);
    const second = await callUnary<ExpenseResponse>(client, 'CreateExpenseV2', request, metadata);
    const [stored] = await testApp.rDataService.expense.findByEventId(event.id);

    expect(second.id).toBe(first.id);
    expect(stored).toHaveLength(1);
  });
});
