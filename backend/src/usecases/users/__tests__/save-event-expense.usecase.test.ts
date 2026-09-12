import {error, success} from '#packages/result';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {CurrencyCode} from '#domain/entities/currency.entity';
import {ExpenseType} from '#domain/entities/expense.entity';
import {
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
  EventDeletedError,
  EventNotFoundError,
  IdempotencyHashMismatchError,
} from '#domain/errors/errors';

import {IdempotencySharedUseCase} from '#usecases/shared/idempotency.usecase';

import {EventService} from '#frameworks/event-service/event-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {SupportedCurrencyService} from '#frameworks/supported-currency-service/supported-currency-service';

import {truncateAllTables} from '#test-support/db';
import {
  TestCase,
  prepareInitRelationalState,
  useFakeTimers,
  validateRelationalStateChanges,
} from '#test-support/relational-state';

import {SaveEventExpenseUseCase} from '../save-event-expense.usecase';

type SaveEventExpenseTestCase = TestCase<SaveEventExpenseUseCase>;

describe('SaveEventExpenseUseCase', () => {
  let relationalDataService: RelationalDataService;
  let useCase: SaveEventExpenseUseCase;
  let eventService: EventServiceAbstract;
  let idempotencySharedUseCase: IdempotencySharedUseCase;

  const mockNow = new Date('2026-01-01T00:00:00.000Z');

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    eventService = new EventService();
    idempotencySharedUseCase = new IdempotencySharedUseCase(relationalDataService);
    useCase = new SaveEventExpenseUseCase(
      relationalDataService,
      eventService,
      new SupportedCurrencyService(relationalDataService),
      idempotencySharedUseCase,
    );

    await relationalDataService.initialize();

    useFakeTimers(mockNow.getTime());
  });

  afterAll(async () => {
    await relationalDataService.destroy();
    jest.useRealTimers();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
    jest.restoreAllMocks();
  });

  const testCases: SaveEventExpenseTestCase[] = [
    {
      name: 'saves the expense without conversion when the currencies match',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-usd',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
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
        eventId: 'event-1',
        currencyId: 'currency-usd',
        description: 'Lunch at restaurant',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        isCustomRate: false,
        splitInformation: [
          {userId: 'user-1', amount: 40, exchangedAmount: 0},
          {userId: 'user-2', amount: 60, exchangedAmount: 0},
        ],
        url: 'url',
      },
      output: success({
        id: expect.any(String),
        eventId: 'event-1',
        currencyId: 'currency-usd',
        description: 'Lunch at restaurant',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        isCustomRate: false,
        splitInformation: [
          {userId: 'user-1', amount: 40, exchangedAmount: 40},
          {userId: 'user-2', amount: 60, exchangedAmount: 60},
        ],
        createdAt: expect.any(Date),
        updatedAt: expect.any(Date),
      }),
      relationalStateChanges: {
        expenses: {
          inserted: [
            {
              id: expect.any(String),
              eventId: 'event-1',
              currencyId: 'currency-usd',
              description: 'Lunch at restaurant',
              userWhoPaidId: 'user-1',
              expenseType: ExpenseType.Expense,
              isCustomRate: false,
              splitInformation: [
                {userId: 'user-1', amount: 40, exchangedAmount: 40},
                {userId: 'user-2', amount: 60, exchangedAmount: 60},
              ],
              createdAt: expect.any(Date),
              updatedAt: expect.any(Date),
            },
          ],
        },
      },
    },
    {
      name: 'saves the expense with currency conversion',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-usd',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
        currencies: [
          {
            id: 'currency-usd',
            code: CurrencyCode.USD,
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
          {
            id: 'currency-eur',
            code: CurrencyCode.EUR,
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
        currencyRates: [
          {
            date: '2026-01-01',
            rate: {
              USD: 1.0,
              EUR: 0.85,
            },
            createdAt: new Date('2026-01-01T00:00:00Z'),
            updatedAt: new Date('2026-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        currencyId: 'currency-eur',
        description: 'Lunch in EUR',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        isCustomRate: false,
        splitInformation: [
          {userId: 'user-1', amount: 40, exchangedAmount: 0},
          {userId: 'user-2', amount: 60, exchangedAmount: 0},
        ],
        url: 'url',
      },
      output: success({
        id: expect.any(String),
        eventId: 'event-1',
        currencyId: 'currency-eur',
        description: 'Lunch in EUR',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        isCustomRate: false,
        splitInformation: [
          {userId: 'user-1', amount: 40, exchangedAmount: 47.06},
          {userId: 'user-2', amount: 60, exchangedAmount: 70.59},
        ],
        createdAt: expect.any(Date),
        updatedAt: expect.any(Date),
      }),
      relationalStateChanges: {
        expenses: {
          inserted: [
            {
              id: expect.any(String),
              eventId: 'event-1',
              currencyId: 'currency-eur',
              description: 'Lunch in EUR',
              userWhoPaidId: 'user-1',
              expenseType: ExpenseType.Expense,
              isCustomRate: false,
              splitInformation: [
                {userId: 'user-1', amount: 40, exchangedAmount: 47.06},
                {userId: 'user-2', amount: 60, exchangedAmount: 70.59},
              ],
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
        currencyId: 'currency-usd',
        description: 'Lunch',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        isCustomRate: false,
        splitInformation: [{userId: 'user-1', amount: 100, exchangedAmount: 0}],
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
            currencyId: 'currency-usd',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: new Date('2023-01-02T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        currencyId: 'currency-usd',
        description: 'Lunch',
        isCustomRate: false,
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: 'user-1', amount: 100, exchangedAmount: 0}],
        url: 'url',
      },
      output: error(new EventDeletedError()),
      relationalStateChanges: {},
    },
    {
      name: 'returns CurrencyNotFoundError when the currency does not exist',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-usd',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
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
        eventId: 'event-1',
        currencyId: 'currency-eur',
        isCustomRate: false,
        description: 'Lunch',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: 'user-1', amount: 100, exchangedAmount: 0}],
        url: 'url',
      },
      output: error(new CurrencyNotFoundError()),
      relationalStateChanges: {},
    },
    {
      name: 'returns CurrencyRateNotFoundError when no rate exists for the date',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-usd',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
        currencies: [
          {
            id: 'currency-usd',
            code: CurrencyCode.USD,
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
          {
            id: 'currency-eur',
            code: CurrencyCode.EUR,
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        isCustomRate: false,
        currencyId: 'currency-eur',
        description: 'Lunch',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: 'user-1', amount: 100, exchangedAmount: 0}],
        url: 'url',
      },
      output: error(new CurrencyRateNotFoundError()),
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
        currencyId: 'currency-usd',
        pinCode: '1234',
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
        deletedAt: null,
      },
    ];
    const currencies = [
      {
        id: 'currency-usd',
        code: CurrencyCode.USD,
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
      },
    ];
    const input = {
      eventId: 'event-1',
      currencyId: 'currency-usd',
      description: 'Lunch at restaurant',
      userWhoPaidId: 'user-1',
      expenseType: ExpenseType.Expense,
      isCustomRate: false,
      splitInformation: [
        {userId: 'user-1', amount: 40, exchangedAmount: 0},
        {userId: 'user-2', amount: 60, exchangedAmount: 0},
      ],
      idempotencyKey: 'key-1',
      url: '/user/event/event-1/expense',
    };

    it('replays the stored response and inserts nothing on a repeated key', async () => {
      await prepareInitRelationalState({rDataService: relationalDataService, initState: {events, currencies}});

      const first = await useCase.execute(input);
      const second = await useCase.execute(input);
      const [expenses] = await relationalDataService.expense.findAll({limit: 10});
      const [keys] = await relationalDataService.idempotencyKey.findAll({limit: 10});

      expect(second).toEqual(JSON.parse(JSON.stringify(first)));
      expect(expenses).toHaveLength(1);
      expect(keys).toEqual([
        expect.objectContaining({key: 'key-1', url: '/user/event/event-1/expense', statusCode: 200}),
      ]);
    });

    it('rejects a repeated key with a different body', async () => {
      await prepareInitRelationalState({rDataService: relationalDataService, initState: {events, currencies}});
      await useCase.execute(input);

      await expect(useCase.execute({...input, description: 'Dinner'})).rejects.toBeInstanceOf(
        IdempotencyHashMismatchError,
      );
    });
  });
});
