import {status} from '@grpc/grpc-js';

import {CurrencyCode} from '#domain/entities/currency.entity';

import {createEvent, findCurrencyIdByCode} from '../support/fixtures';
import {UserServiceClient, callUnary, createUserServiceClient, expectGrpcError} from '../support/grpc-client';
import {TestApp, createTestApp} from '../support/test-app';

describe('gRPC event protocol compatibility', () => {
  let testApp: TestApp;
  let mobile: UserServiceClient;
  let generic: UserServiceClient;
  let compatible: UserServiceClient;
  let currencyId: string;

  beforeAll(async () => {
    testApp = await createTestApp({grpc: true});
    if (!testApp.grpcUrl) {
      throw new Error('Missing gRPC URL');
    }
    mobile = createUserServiceClient(testApp.grpcUrl, {'grpc.primary_user_agent': 'CommonEx/9 (Android/r)'});
    generic = createUserServiceClient(testApp.grpcUrl);
    compatible = createUserServiceClient(testApp.grpcUrl, {'grpc.primary_user_agent': 'CommonEx/11 (iOS/r)'});
  });

  afterAll(async () => {
    mobile.close();
    generic.close();
    compatible.close();
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
    const currentResponse = await callUnary<{id: string; version: number}>(compatible, 'GetEventInfoV2', {
      eventId: current.id,
      pinCode: '1234',
    });
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

  it('accepts event version two starting with mobile release eleven', async () => {
    const request = {name: 'Trip', currencyId, pinCode: '1234', users: [{name: 'Alice'}], version: 2};
    const error = await expectGrpcError(callUnary(mobile, 'CreateEvent', request));
    expect(error).toMatchObject({code: status.FAILED_PRECONDITION, errorCode: 'B4020'});
    const response = await callUnary<{version: number}>(compatible, 'CreateEvent', request);
    expect(response.version).toBe(2);
    const legacy = await callUnary<{version: number}>(mobile, 'CreateEvent', {
      name: 'Old trip',
      currencyId,
      pinCode: '1234',
      users: [{name: 'Alice'}],
    });
    expect(legacy.version).toBe(1);
  });

  it('creates version two automatically for mobile release eleven without a version field', async () => {
    const response = await callUnary<{id: string; version: number}>(compatible, 'CreateEvent', {
      name: 'Trip',
      currencyId,
      pinCode: '1234',
      users: [{name: 'Alice'}],
    });
    expect(response.version).toBe(2);
    const [stored] = await testApp.rDataService.event.findById(response.id);
    expect(stored?.version).toBe(2);
    const error = await expectGrpcError(callUnary(mobile, 'GetEventInfoV2', {eventId: response.id, pinCode: '1234'}));
    expect(error).toMatchObject({code: status.FAILED_PRECONDITION, errorCode: 'B4020'});
  });
});
