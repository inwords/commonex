import {error, success} from '#packages/result';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {
  EventDeletedError,
  EventNotFoundError,
  IdempotencyHashMismatchError,
  InvalidPinCodeError,
} from '#domain/errors/errors';

import {IdempotencySharedUseCase} from '#usecases/shared/idempotency.usecase';

import {EventService} from '#frameworks/event-service/event-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {truncateAllTables} from '#test-support/db';
import {TestCase, prepareInitRelationalState, validateRelationalStateChanges} from '#test-support/relational-state';

import {SaveUsersToEventUseCase} from '../save-users-to-event.usecase';

type SaveUsersToEventTestCase = TestCase<SaveUsersToEventUseCase>;

describe('SaveUsersToEventUseCase', () => {
  let relationalDataService: RelationalDataService;
  let useCase: SaveUsersToEventUseCase;
  let eventService: EventServiceAbstract;
  let idempotencySharedUseCase: IdempotencySharedUseCase;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    eventService = new EventService();
    idempotencySharedUseCase = new IdempotencySharedUseCase(relationalDataService);
    useCase = new SaveUsersToEventUseCase(relationalDataService, eventService, idempotencySharedUseCase);

    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
    jest.restoreAllMocks();
  });

  const testCases: SaveUsersToEventTestCase[] = [
    {
      name: 'saves users to the event',
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
        users: [
          {
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
          {
            name: 'Jane Smith',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
        url: 'url',
      },
      output: success([
        {
          id: expect.any(String),
          eventId: 'event-1',
          name: 'John Doe',
          createdAt: expect.any(Date),
          updatedAt: expect.any(Date),
        },
        {
          id: expect.any(String),
          eventId: 'event-1',
          name: 'Jane Smith',
          createdAt: expect.any(Date),
          updatedAt: expect.any(Date),
        },
      ]),
      relationalStateChanges: {
        userInfos: {
          inserted: [
            {
              id: expect.any(String),
              eventId: 'event-1',
              name: 'John Doe',
              createdAt: expect.any(Date),
              updatedAt: expect.any(Date),
            },
            {
              id: expect.any(String),
              eventId: 'event-1',
              name: 'Jane Smith',
              createdAt: expect.any(Date),
              updatedAt: expect.any(Date),
            },
          ],
        },
      },
    },
    {
      name: 'returns EventNotFoundError when the event does not exist',
      initRelationalState: {},
      input: {
        eventId: 'non-existent',
        pinCode: '1234',
        users: [
          {
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
        url: 'url',
      },
      output: error(new EventNotFoundError()),
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
        users: [
          {
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
        url: 'url',
      },
      output: error(new EventDeletedError()),
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
        users: [
          {
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
        url: 'url',
      },
      output: error(new InvalidPinCodeError()),
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

  describe('idempotency', () => {
    const events = [
      {
        id: 'event-1',
        name: 'Test Event',
        currencyId: 'currency-1',
        pinCode: '1234',
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
        deletedAt: null,
      },
    ];
    const input = {
      eventId: 'event-1',
      pinCode: '1234',
      users: [
        {name: 'Alice', createdAt: new Date('2023-01-01T00:00:00Z'), updatedAt: new Date('2023-01-01T00:00:00Z')},
      ],
      idempotencyKey: 'key-1',
      url: '/user/event/event-1/users',
    };

    it('replays the stored response and inserts nothing on a repeated key', async () => {
      await prepareInitRelationalState({rDataService: relationalDataService, initState: {events}});

      const first = await useCase.execute(input);
      const second = await useCase.execute(input);
      const [userInfos] = await relationalDataService.userInfo.findAll({limit: 10});
      const [keys] = await relationalDataService.idempotencyKey.findAll({limit: 10});

      expect(second).toEqual(JSON.parse(JSON.stringify(first)));
      expect(userInfos).toHaveLength(1);
      expect(keys).toEqual([
        expect.objectContaining({key: 'key-1', url: '/user/event/event-1/users', statusCode: 200}),
      ]);
    });

    it('rejects a repeated key with a different body', async () => {
      await prepareInitRelationalState({rDataService: relationalDataService, initState: {events}});
      await useCase.execute(input);

      const otherUsers = [
        {name: 'Bob', createdAt: new Date('2023-01-01T00:00:00Z'), updatedAt: new Date('2023-01-01T00:00:00Z')},
      ];

      await expect(useCase.execute({...input, users: otherUsers})).rejects.toBeInstanceOf(IdempotencyHashMismatchError);
    });
  });
});
