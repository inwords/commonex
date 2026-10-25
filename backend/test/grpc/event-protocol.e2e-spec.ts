import {Metadata, status} from '@grpc/grpc-js';

import {CurrencyCode} from '#domain/entities/currency.entity';

import {createEvent, findCurrencyIdByCode} from '../support/fixtures';
import {UserServiceClient, callUnary, createUserServiceClient, expectGrpcError} from '../support/grpc-client';
import {TestApp, createTestApp} from '../support/test-app';

describe('gRPC event protocol compatibility', () => {
  let testApp: TestApp;
  let mobile: UserServiceClient;
  let generic: UserServiceClient;
  let currencyId: string;

  beforeAll(async () => {
    testApp = await createTestApp({grpc: true});
    if (!testApp.grpcUrl) {
      throw new Error('Missing gRPC URL');
    }
    mobile = createUserServiceClient(testApp.grpcUrl, {'grpc.primary_user_agent': 'CommonEx/123 (Android/r)'});
    generic = createUserServiceClient(testApp.grpcUrl);
  });

  afterAll(async () => {
    mobile.close();
    generic.close();
    await testApp.close();
  });

  beforeEach(async () => {
    await testApp.reset();
    currencyId = await findCurrencyIdByCode(testApp.rDataService, CurrencyCode.USD);
  });

  it('keeps legacy events accessible and blocks new events for old mobile callers', async () => {
    const legacy = await createEvent(testApp.app, {currencyId});
    const current = await createEvent(testApp.app, {currencyId, version: 2});
    const legacyResponse = await callUnary<{id: string; version: number}>(mobile, 'GetEventInfoV2', {
      eventId: legacy.id,
      pinCode: '1234',
    });
    expect(legacyResponse).toMatchObject({id: legacy.id, version: 1});
    const error = await expectGrpcError(callUnary(mobile, 'GetEventInfoV2', {eventId: current.id, pinCode: '1234'}));
    expect(error).toMatchObject({code: status.FAILED_PRECONDITION, errorCode: 'B4020'});
    const compatible = new Metadata();
    compatible.set('x-commonex-version', '2');
    const currentResponse = await callUnary<{id: string; version: number}>(
      mobile,
      'GetEventInfoV2',
      {eventId: current.id, pinCode: '1234'},
      compatible,
    );
    expect(currentResponse).toMatchObject({id: current.id, version: 2});
    const genericResponse = await callUnary<{id: string}>(generic, 'GetEventInfoV2', {
      eventId: current.id,
      pinCode: '1234',
    });
    expect(genericResponse.id).toBe(current.id);
  });

  it('protects legacy reads and mutations without requiring a prior join', async () => {
    const event = await createEvent(testApp.app, {currencyId, version: 2});
    const calls = [
      callUnary(mobile, 'GetEventInfo', {eventId: event.id, pinCode: '1234'}),
      callUnary(mobile, 'GetAllEventExpenses', {eventId: event.id}),
      callUnary(mobile, 'GetAllEventExpensesV2', {eventId: event.id, pinCode: '1234'}),
      callUnary(mobile, 'AddUsersToEventV2', {eventId: event.id, pinCode: '1234', users: [{name: 'Bob'}]}),
      callUnary(mobile, 'CreateEventShareTokenV2', {eventId: event.id, pinCode: '1234'}),
      callUnary(mobile, 'DeleteEvent', {eventId: event.id, pinCode: '1234'}),
    ];
    const errors = await Promise.all(calls.map(expectGrpcError));
    for (const error of errors) {
      expect(error).toMatchObject({code: status.FAILED_PRECONDITION, errorCode: 'B4020'});
    }
    const [stored] = await testApp.rDataService.event.findById(event.id);
    expect(stored?.deletedAt).toBeNull();
  });

  it('accepts protocol two only when the mobile caller advertises support', async () => {
    const request = {name: 'Trip', currencyId, pinCode: '1234', users: [{name: 'Alice'}], version: 2};
    const error = await expectGrpcError(callUnary(mobile, 'CreateEvent', request));
    expect(error).toMatchObject({code: status.FAILED_PRECONDITION, errorCode: 'B4020'});
    const compatible = new Metadata();
    compatible.set('x-commonex-version', '2');
    const response = await callUnary<{version: number}>(mobile, 'CreateEvent', request, compatible);
    expect(response.version).toBe(2);
    const legacy = await callUnary<{version: number}>(mobile, 'CreateEvent', {
      name: 'Old trip',
      currencyId,
      pinCode: '1234',
      users: [{name: 'Alice'}],
    });
    expect(legacy.version).toBe(1);
  });
});
