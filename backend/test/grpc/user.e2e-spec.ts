import {Metadata, status} from '@grpc/grpc-js';

import {CurrencyCode} from '#domain/entities/currency.entity';
import {ExpenseType} from '#domain/entities/expense.entity';
import {ExpenseValueObject} from '#domain/value-objects/expense.value-object';

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
  revertsExpenseId?: string;
  replacesExpenseId?: string;
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

  it('round-trips reversal links and normalizes correction errors', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});
    const [alice] = event.users;
    const request = {
      eventId: event.id,
      pinCode: '1234',
      description: 'Lunch',
      userWhoPaidId: alice?.id,
      currencyId: usdId,
      expenseType: 'expense',
      splitInformation: [{userId: alice?.id, amount: 40.5}],
    };
    const original = await callUnary<ExpenseResponse>(client, 'CreateExpenseV2', request);

    const missingReference = await expectGrpcError(
      callUnary(client, 'CreateExpenseV2', {
        ...request,
        replacesExpenseId: 'missing',
      }),
    );
    expect(missingReference).toEqual({
      code: status.INVALID_ARGUMENT,
      details: 'Referenced expense not found in event',
      errorCode: 'B4012',
    });

    const invalidReversal = await expectGrpcError(
      callUnary(client, 'CreateExpenseV2', {
        ...request,
        revertsExpenseId: original.id,
        replacesExpenseId: original.id,
      }),
    );
    expect(invalidReversal).toEqual({
      code: status.INVALID_ARGUMENT,
      details: 'Expense correction is invalid',
      errorCode: 'B4014',
    });

    const reversalRequest = {
      ...request,
      description: 'Undo lunch',
      expenseType: 'refund',
      splitInformation: [{userId: alice?.id, amount: -40.5}],
      revertsExpenseId: original.id,
    };
    const reversal = await callUnary<ExpenseResponse>(client, 'CreateExpenseV2', reversalRequest);
    expect(reversal.revertsExpenseId).toBe(original.id);
    expect(reversal.expenseType).toBe('refund');
    expect(reversal.splitInformation).toEqual([{userId: alice?.id, amount: -40.5, exchangedAmount: -40.5}]);
    expect(new Date(reversal.createdAt).toISOString()).toBe(reversal.createdAt);

    const duplicate = await expectGrpcError(callUnary(client, 'CreateExpenseV2', reversalRequest));
    expect(duplicate).toEqual({
      code: status.ALREADY_EXISTS,
      details: 'Expense is already reverted',
      errorCode: 'B4013',
    });

    const response = await callUnary<{expenses: ExpenseResponse[]}>(client, 'GetAllEventExpensesV2', {
      eventId: event.id,
      pinCode: '1234',
    });
    expect(response.expenses).toHaveLength(2);
    expect(response.expenses).toContainEqual(expect.objectContaining({id: reversal.id, revertsExpenseId: original.id}));
  });

  it('ignores redundant reversal financial values and preserves historical converted rows without rates', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});
    const eurId = await findCurrencyIdByCode(testApp.rDataService, CurrencyCode.EUR);
    const [alice, bob] = event.users;
    if (alice === undefined || bob === undefined) throw new Error('Expected event participants');
    const original = new ExpenseValueObject({
      eventId: event.id,
      currencyId: eurId,
      description: 'Refund',
      userWhoPaidId: alice.id,
      expenseType: ExpenseType.Refund,
      isCustomRate: true,
      splitInformation: [
        {userId: bob.id, amount: -10, exchangedAmount: -12.5},
        {userId: bob.id, amount: -10, exchangedAmount: -12.51},
      ],
    }).value;
    await testApp.rDataService.expense.insert(original);
    const createdAt = '2026-02-03T04:05:06.000Z';
    const reversal = await callUnary<ExpenseResponse>(client, 'CreateExpenseV2', {
      eventId: event.id,
      pinCode: '1234',
      description: 'Undo refund',
      createdAt,
      revertsExpenseId: original.id,
      currencyId: 'missing',
      userWhoPaidId: 'wrong',
      expenseType: 'refund',
      isCustomRate: false,
      splitInformation: [{userId: 'wrong', amount: 999, exchangedAmount: 11}],
    });
    expect(reversal).toMatchObject({
      currencyId: eurId,
      userWhoPaidId: original.userWhoPaidId,
      expenseType: 'expense',
      isCustomRate: true,
      description: 'Undo refund',
      createdAt,
      revertsExpenseId: original.id,
      splitInformation: [
        {userId: bob.id, amount: 10, exchangedAmount: 12.5},
        {userId: bob.id, amount: 10, exchangedAmount: 12.51},
      ],
    });
    const [stored] = await testApp.rDataService.expense.findById(reversal.id);
    expect(stored).toMatchObject({
      ...reversal,
      createdAt: new Date(createdAt),
      updatedAt: new Date(reversal.updatedAt),
    });
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
