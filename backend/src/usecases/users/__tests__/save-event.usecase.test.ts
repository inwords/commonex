import {error, success} from '#packages/result';

import {CurrencyCode} from '#domain/entities/currency.entity';
import {CurrencyNotFoundError, IdempotencyHashMismatchError} from '#domain/errors/errors';

import {IdempotencySharedUseCase} from '#usecases/shared/idempotency.usecase';

import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {SupportedCurrencyService} from '#frameworks/supported-currency-service/supported-currency-service';

import {truncateAllTables} from '#test-support/db';
import {TestCase, prepareInitRelationalState, validateRelationalStateChanges} from '#test-support/relational-state';

import {SaveEventUseCase} from '../save-event.usecase';

type SaveEventTestCase = TestCase<SaveEventUseCase>;

describe('SaveEventUseCase', () => {
  let relationalDataService: RelationalDataService;
  let useCase: SaveEventUseCase;
  let idempotencySharedUseCase: IdempotencySharedUseCase;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    idempotencySharedUseCase = new IdempotencySharedUseCase(relationalDataService);
    useCase = new SaveEventUseCase(
      relationalDataService,
      new SupportedCurrencyService(relationalDataService),
      idempotencySharedUseCase,
    );

    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
    jest.restoreAllMocks();
  });

  const testCases: SaveEventTestCase[] = [
    {
      name: 'creates an event with users',
      initRelationalState: {
        currencies: [
          {
            id: 'currency-usd',
            code: CurrencyCode.USD,
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        event: {
          name: 'New Event',
          currencyId: 'currency-usd',
          pinCode: '1234',
        },
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
      output: success({
        id: expect.any(String),
        name: 'New Event',
        currencyId: 'currency-usd',
        pinCode: '1234',
        createdAt: expect.any(Date),
        updatedAt: expect.any(Date),
        deletedAt: null,
        users: [
          {
            id: expect.any(String),
            eventId: expect.any(String),
            name: 'John Doe',
            createdAt: expect.any(Date),
            updatedAt: expect.any(Date),
          },
          {
            id: expect.any(String),
            eventId: expect.any(String),
            name: 'Jane Smith',
            createdAt: expect.any(Date),
            updatedAt: expect.any(Date),
          },
        ],
      }),
      relationalStateChanges: {
        events: {
          inserted: [
            {
              id: expect.any(String),
              name: 'New Event',
              currencyId: 'currency-usd',
              pinCode: '1234',
              createdAt: expect.any(Date),
              updatedAt: expect.any(Date),
              deletedAt: null,
            },
          ],
        },
        userInfos: {
          inserted: [
            {
              id: expect.any(String),
              eventId: expect.any(String),
              name: 'John Doe',
              createdAt: expect.any(Date),
              updatedAt: expect.any(Date),
            },
            {
              id: expect.any(String),
              eventId: expect.any(String),
              name: 'Jane Smith',
              createdAt: expect.any(Date),
              updatedAt: expect.any(Date),
            },
          ],
        },
      },
    },
    {
      name: 'returns CurrencyNotFoundError when the currency does not exist',
      initRelationalState: {},
      input: {
        event: {
          name: 'New Event',
          currencyId: 'non-existent',
          pinCode: '1234',
        },
        users: [
          {
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
        url: 'url',
      },
      output: error(new CurrencyNotFoundError()),
      relationalStateChanges: {},
    },
    {
      name: 'returns CurrencyNotFoundError when the currency exists but is not yet supported',
      initRelationalState: {
        currencies: [
          {
            id: 'currency-zzz',
            code: 'ZZZ' as CurrencyCode,
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        event: {
          name: 'New Event',
          currencyId: 'currency-zzz',
          pinCode: '1234',
        },
        users: [
          {
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
        url: 'url',
      },
      output: error(new CurrencyNotFoundError()),
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
    const input = {
      event: {name: 'Trip', currencyId: 'currency-usd', pinCode: '1234'},
      users: [
        {name: 'Alice', createdAt: new Date('2023-01-01T00:00:00Z'), updatedAt: new Date('2023-01-01T00:00:00Z')},
      ],
      idempotencyKey: 'key-1',
      url: '/user/event',
    };
    const currencies = [
      {
        id: 'currency-usd',
        code: CurrencyCode.USD,
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
      },
    ];

    it('replays the stored response and inserts nothing on a repeated key', async () => {
      await prepareInitRelationalState({rDataService: relationalDataService, initState: {currencies}});

      const first = await useCase.execute(input);
      const second = await useCase.execute(input);
      const [events] = await relationalDataService.event.findAll({limit: 10});
      const [keys] = await relationalDataService.idempotencyKey.findAll({limit: 10});

      expect(second).toEqual(JSON.parse(JSON.stringify(first)));
      expect(events).toHaveLength(1);
      expect(keys).toEqual([expect.objectContaining({key: 'key-1', url: '/user/event', statusCode: 200})]);
    });

    it('rejects a repeated key with a different body', async () => {
      await prepareInitRelationalState({rDataService: relationalDataService, initState: {currencies}});
      await useCase.execute(input);

      await expect(useCase.execute({...input, event: {...input.event, name: 'Other'}})).rejects.toBeInstanceOf(
        IdempotencyHashMismatchError,
      );
    });
  });
});
