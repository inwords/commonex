import {error, success} from '#packages/result';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {EventDeletedError, EventNotFoundError, InvalidPinCodeError} from '#domain/errors/errors';

import {EventService} from '#frameworks/event-service/event-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {truncateAllTables} from '#test-support/db';
import {
  TestCase,
  prepareInitRelationalState,
  useFakeTimers,
  validateRelationalStateChanges,
} from '#test-support/relational-state';

import {CreateEventShareTokenV2UseCase} from '../create-event-share-token-v2.usecase';

type CreateEventShareTokenV2TestCase = TestCase<CreateEventShareTokenV2UseCase>;

describe('CreateEventShareTokenV2UseCase', () => {
  let relationalDataService: RelationalDataService;
  let useCase: CreateEventShareTokenV2UseCase;
  let eventService: EventServiceAbstract;

  const mockNow = new Date('2026-01-01T00:00:00.000Z');
  const mockExpiry = new Date('2026-01-15T00:00:00.000Z');

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    eventService = new EventService();
    useCase = new CreateEventShareTokenV2UseCase(relationalDataService, eventService);

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

  const testCases: CreateEventShareTokenV2TestCase[] = [
    {
      name: 'creates a new token when there is no active token',
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
        pinCode: '1234',
      },
      output: success({
        token: expect.any(String),
        expiresAt: mockExpiry.toISOString(),
      }),
      relationalStateChanges: {
        eventShareTokens: {
          inserted: [
            {
              token: expect.any(String),
              eventId: 'event-1',
              expiresAt: mockExpiry,
              createdAt: mockNow,
            },
          ],
        },
      },
    },
    {
      name: 'reuses an existing active token',
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
            token: 'existing-token-123',
            eventId: 'event-1',
            expiresAt: new Date('2026-12-31T00:00:00Z'),
            createdAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        pinCode: '1234',
      },
      output: success({
        token: 'existing-token-123',
        expiresAt: '2026-12-31T00:00:00.000Z',
      }),
      relationalStateChanges: {},
    },
    {
      name: 'returns EventNotFoundError when the event does not exist',
      initRelationalState: {},
      input: {
        eventId: 'non-existent',
        pinCode: '1234',
      },
      output: error(new EventNotFoundError()),
      relationalStateChanges: {},
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
      relationalStateChanges: {},
    },
    {
      name: 'returns EventDeletedError when the event is deleted',
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
      relationalStateChanges: {},
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

      if (testCase.relationalStateChanges) {
        await validateRelationalStateChanges({
          rDataService: relationalDataService,
          initState: testCase.initRelationalState,
          stateChanges: testCase.relationalStateChanges,
        });
      }
    });
  });
});
