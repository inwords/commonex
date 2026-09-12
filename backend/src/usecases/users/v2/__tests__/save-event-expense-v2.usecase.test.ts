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
  InconsistentExchangedAmountError,
  InvalidPinCodeError,
} from '#domain/errors/errors';

import {IdempotencySharedUseCase} from '#usecases/shared/idempotency.usecase';
import {SaveEventExpenseV2UseCase} from '#usecases/users/v2';

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

type SaveEventExpenseV2TestCase = TestCase<SaveEventExpenseV2UseCase>;

const SAVE_EXPENSE_V2_URL = '/v2/user/event/event-1/expense';

describe('SaveEventExpenseV2UseCase', () => {
  let relationalDataService: RelationalDataService;
  let useCase: SaveEventExpenseV2UseCase;
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
    useCase = new SaveEventExpenseV2UseCase(
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

  const testCases: SaveEventExpenseV2TestCase[] = [
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
        splitInformation: [
          {userId: 'user-1', amount: 40},
          {userId: 'user-2', amount: 60},
        ],
        pinCode: '1234',
        url: 'url',
      },
      output: success({
        id: expect.any(String),
        eventId: 'event-1',
        currencyId: 'currency-usd',
        description: 'Lunch at restaurant',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [
          {userId: 'user-1', amount: 40, exchangedAmount: 40},
          {userId: 'user-2', amount: 60, exchangedAmount: 60},
        ],
        isCustomRate: false,
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
              splitInformation: [
                {userId: 'user-1', amount: 40, exchangedAmount: 40},
                {userId: 'user-2', amount: 60, exchangedAmount: 60},
              ],
              isCustomRate: false,
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
        splitInformation: [
          {userId: 'user-1', amount: 40},
          {userId: 'user-2', amount: 60},
        ],
        pinCode: '1234',
        url: 'url',
      },
      output: success({
        id: expect.any(String),
        eventId: 'event-1',
        currencyId: 'currency-eur',
        description: 'Lunch in EUR',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [
          {userId: 'user-1', amount: 40, exchangedAmount: 47.06},
          {userId: 'user-2', amount: 60, exchangedAmount: 70.59},
        ],
        isCustomRate: false,
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
              splitInformation: [
                {userId: 'user-1', amount: 40, exchangedAmount: 47.06},
                {userId: 'user-2', amount: 60, exchangedAmount: 70.59},
              ],
              isCustomRate: false,
              createdAt: expect.any(Date),
              updatedAt: expect.any(Date),
            },
          ],
        },
      },
    },
    {
      name: 'saves the expense with a custom exchange rate',
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
        description: 'Lunch in EUR with custom rate',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [
          // Custom rate: 1.25 instead of the automatic 1.176
          {userId: 'user-1', amount: 40, exchangedAmount: 50},
          {userId: 'user-2', amount: 60, exchangedAmount: 75},
        ],
        pinCode: '1234',
        url: 'url',
      },
      output: success({
        id: expect.any(String),
        eventId: 'event-1',
        currencyId: 'currency-eur',
        description: 'Lunch in EUR with custom rate',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [
          {userId: 'user-1', amount: 40, exchangedAmount: 50},
          {userId: 'user-2', amount: 60, exchangedAmount: 75},
        ],
        isCustomRate: true,
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
              description: 'Lunch in EUR with custom rate',
              userWhoPaidId: 'user-1',
              expenseType: ExpenseType.Expense,
              splitInformation: [
                {userId: 'user-1', amount: 40, exchangedAmount: 50},
                {userId: 'user-2', amount: 60, exchangedAmount: 75},
              ],
              isCustomRate: true,
              createdAt: expect.any(Date),
              updatedAt: expect.any(Date),
            },
          ],
        },
      },
    },
    {
      name: 'uses the rate of the createdAt date instead of today',
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
            rate: {USD: 1, EUR: 0.85},
            createdAt: new Date('2026-01-01T00:00:00Z'),
            updatedAt: new Date('2026-01-01T00:00:00Z'),
          },
          {
            date: '2025-12-15',
            rate: {USD: 1, EUR: 0.5},
            createdAt: new Date('2025-12-15T00:00:00Z'),
            updatedAt: new Date('2025-12-15T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        currencyId: 'currency-eur',
        description: 'Old dinner',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: 'user-1', amount: 10}],
        pinCode: '1234',
        createdAt: new Date('2025-12-15T18:00:00Z'),
        url: 'url',
      },
      output: success(
        expect.objectContaining({
          createdAt: new Date('2025-12-15T18:00:00Z'),
          splitInformation: [{userId: 'user-1', amount: 10, exchangedAmount: 20}],
        }),
      ),
      relationalStateChanges: {
        expenses: {
          inserted: [
            expect.objectContaining({
              splitInformation: [{userId: 'user-1', amount: 10, exchangedAmount: 20}],
            }),
          ],
        },
      },
    },
    {
      name: 'returns InconsistentExchangedAmountError when exchangedAmount is set for only some splits',
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
        currencyId: 'currency-eur',
        description: 'Lunch',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [
          // has exchangedAmount
          {userId: 'user-1', amount: 40, exchangedAmount: 50},
          // missing exchangedAmount (undefined)
          {userId: 'user-2', amount: 60},
        ],
        pinCode: '1234',
        url: 'url',
      },
      output: error(new InconsistentExchangedAmountError()),
      relationalStateChanges: {},
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
        splitInformation: [{userId: 'user-1', amount: 100}],
        pinCode: '1234',
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
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: 'user-1', amount: 100}],
        pinCode: '1234',
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
            currencyId: 'currency-usd',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
      },
      input: {
        eventId: 'event-1',
        currencyId: 'currency-usd',
        description: 'Lunch',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: 'user-1', amount: 100}],
        pinCode: 'wrong',
        url: 'url',
      },
      output: error(new InvalidPinCodeError()),
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
        description: 'Lunch',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: 'user-1', amount: 100}],
        pinCode: '1234',
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
        currencyId: 'currency-eur',
        description: 'Lunch',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: 'user-1', amount: 100}],
        pinCode: '1234',
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
      splitInformation: [
        {userId: 'user-1', amount: 40},
        {userId: 'user-2', amount: 60},
      ],
      pinCode: '1234',
      idempotencyKey: 'key-1',
      url: SAVE_EXPENSE_V2_URL,
    };

    it('replays the stored response and inserts nothing on a repeated key', async () => {
      await prepareInitRelationalState({rDataService: relationalDataService, initState: {events, currencies}});

      const first = await useCase.execute(input);
      const second = await useCase.execute(input);
      const [expenses] = await relationalDataService.expense.findAll({limit: 10});
      const [keys] = await relationalDataService.idempotencyKey.findAll({limit: 10});

      expect(second).toEqual(JSON.parse(JSON.stringify(first)));
      expect(expenses).toHaveLength(1);
      expect(keys).toEqual([expect.objectContaining({key: 'key-1', url: SAVE_EXPENSE_V2_URL, statusCode: 200})]);
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
