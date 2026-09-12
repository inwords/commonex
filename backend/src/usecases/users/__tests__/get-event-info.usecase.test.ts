import {error, success} from '#packages/result';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {EventDeletedError, EventNotFoundError, InvalidPinCodeError} from '#domain/errors/errors';

import {EventService} from '#frameworks/event-service/event-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {truncateAllTables} from '#test-support/db';
import {TestCase, prepareInitRelationalState} from '#test-support/relational-state';

import {GetEventInfoUseCase} from '../get-event-info.usecase';

type GetEventInfoTestCase = TestCase<GetEventInfoUseCase>;

describe('GetEventInfoUseCase', () => {
  let relationalDataService: RelationalDataService;
  let useCase: GetEventInfoUseCase;
  let eventService: EventServiceAbstract;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    eventService = new EventService();
    useCase = new GetEventInfoUseCase(relationalDataService, eventService);

    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
    jest.clearAllMocks();
  });

  const testCases: GetEventInfoTestCase[] = [
    {
      name: 'returns event info with users',
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
      name: 'returns EventNotFoundError when the event does not exist',
      initRelationalState: {},
      input: {
        eventId: 'non-existent',
        pinCode: '1234',
      },
      output: error(new EventNotFoundError()),
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
