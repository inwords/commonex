import {error, success} from '#packages/result';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {
  EventDeletedError,
  EventNotFoundError,
  InvalidPinCodeError,
  InvalidTokenError,
  TokenExpiredError,
} from '#domain/errors/errors';

import {GetEventInfoV2UseCase} from '#usecases/users/v2';

import {EventService} from '#frameworks/event-service/event-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {truncateAllTables} from '#test-support/db';
import {TestCase, prepareInitRelationalState, useFakeTimers} from '#test-support/relational-state';

type GetEventInfoV2TestCase = TestCase<GetEventInfoV2UseCase>;

describe('GetEventInfoV2UseCase', () => {
  let relationalDataService: RelationalDataService;
  let useCase: GetEventInfoV2UseCase;
  let eventService: EventServiceAbstract;

  const mockNow = new Date('2026-01-01T00:00:00.000Z');

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    eventService = new EventService();
    useCase = new GetEventInfoV2UseCase(relationalDataService, eventService);

    await relationalDataService.initialize();

    useFakeTimers(mockNow.getTime());
  });

  afterAll(async () => {
    await relationalDataService.destroy();
    jest.useRealTimers();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
    jest.clearAllMocks();
  });

  const testCases: GetEventInfoV2TestCase[] = [
    {
      name: 'returns the event info with a valid pin code',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-1',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
        userInfos: [
          {
            id: 'user-1',
            eventId: 'event-1',
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
          {
            id: 'user-2',
            eventId: 'event-1',
            name: 'Jane Smith',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        pinCode: '1234',
      },
      output: success({
        id: 'event-1',
        name: 'Test Event',
        currencyId: 'currency-1',
        pinCode: '1234',
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
        deletedAt: null,
        users: [
          {
            id: 'user-1',
            eventId: 'event-1',
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
          {
            id: 'user-2',
            eventId: 'event-1',
            name: 'Jane Smith',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      }),
    },
    {
      name: 'returns the event info with a valid token',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-1',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
        userInfos: [
          {
            id: 'user-1',
            eventId: 'event-1',
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
        eventShareTokens: [
          {
            token: 'valid-token-123',
            eventId: 'event-1',
            expiresAt: new Date('2026-12-31T00:00:00Z'),
            createdAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        token: 'valid-token-123',
      },
      output: success({
        id: 'event-1',
        name: 'Test Event',
        currencyId: 'currency-1',
        pinCode: '1234',
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
        deletedAt: null,
        users: [
          {
            id: 'user-1',
            eventId: 'event-1',
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      }),
    },
    {
      name: 'returns EventNotFoundError when the event does not exist (with pin code)',
      initRelationalState: {},
      input: {
        eventId: 'non-existent',
        pinCode: '1234',
      },
      output: error(new EventNotFoundError()),
    },
    {
      name: 'returns EventDeletedError when the event is deleted (with pin code)',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-1',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: new Date('2023-01-02T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        pinCode: '1234',
      },
      output: error(new EventDeletedError()),
    },
    {
      name: 'returns InvalidPinCodeError when the pin code is wrong',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-1',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
      },
      input: {
        eventId: 'event-1',
        pinCode: 'wrong',
      },
      output: error(new InvalidPinCodeError()),
    },
    {
      name: 'returns InvalidTokenError when the token is not found',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-1',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
      },
      input: {
        eventId: 'event-1',
        token: 'invalid-token',
      },
      output: error(new InvalidTokenError()),
    },
    {
      name: 'returns InvalidTokenError when the token does not match the eventId',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-1',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
        eventShareTokens: [
          {
            token: 'valid-token-123',
            eventId: 'event-2',
            expiresAt: new Date('2026-12-31T00:00:00Z'),
            createdAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        token: 'valid-token-123',
      },
      output: error(new InvalidTokenError()),
    },
    {
      name: 'returns TokenExpiredError when the token has expired',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-1',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
        eventShareTokens: [
          {
            token: 'expired-token-123',
            eventId: 'event-1',
            expiresAt: new Date('2025-12-31T00:00:00Z'),
            createdAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        token: 'expired-token-123',
      },
      output: error(new TokenExpiredError()),
    },
    {
      name: 'returns EventNotFoundError when the event does not exist (with token)',
      initRelationalState: {},
      input: {
        eventId: 'non-existent',
        token: 'some-token',
      },
      output: error(new EventNotFoundError()),
    },
    {
      name: 'returns EventDeletedError when the event is deleted (with token)',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-1',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: new Date('2023-01-02T00:00:00Z'),
          },
        ],
        eventShareTokens: [
          {
            token: 'valid-token-123',
            eventId: 'event-1',
            expiresAt: new Date('2026-12-31T00:00:00Z'),
            createdAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        token: 'valid-token-123',
      },
      output: error(new EventDeletedError()),
    },
  ];

  testCases.forEach((testCase) => {
    it(testCase.name, async () => {
      await prepareInitRelationalState({
        rDataService: relationalDataService,
        initState: testCase.initRelationalState,
      });

      const result = await useCase.execute(testCase.input);

      expect(result).toEqual(testCase.output);
    });
  });
});
