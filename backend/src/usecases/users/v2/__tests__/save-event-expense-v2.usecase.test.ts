import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {SaveEventExpenseV2UseCase} from '#usecases/users/v2';
import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {TestCase, prepareInitRelationalState, validateRelationalStateChanges, useFakeTimers} from '../../../__tests__/test-helpers';
import {Result, error, success} from '#packages/result';
import {
  EventNotFoundError,
  EventDeletedError,
  InvalidPinCodeError,
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
  InconsistentExchangedAmountError,
} from '#domain/errors/errors';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {EventService} from '#frameworks/event-service/event-service';
import {CurrencyCode} from '#domain/entities/currency.entity';
import {ExpenseType} from '#domain/entities/expense.entity';
import {SupportedCurrencyService} from '#frameworks/supported-currency-service/supported-currency-service';
import {IdempotencySharedUseCase} from '#usecases/shared/idempotency.usecase';

type SaveEventExpenseV2TestCase = TestCase<SaveEventExpenseV2UseCase> & {
  mockEventService: {
    isValidEvent: Result<boolean, EventNotFoundError | EventDeletedError | InvalidPinCodeError>;
  };
  mockIdempotencyUseCase?: {execute: Awaited<ReturnType<SaveEventExpenseV2UseCase['execute']>>};
};

const SAVE_EXPENSE_V2_URL = '/v2/user/event/event-1/expense';

describe('SaveEventExpenseV2UseCase', () => {
  let relationalDataService: RelationalDataServiceAbstract;
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
    useCase = new SaveEventExpenseV2UseCase(relationalDataService, eventService, new SupportedCurrencyService(relationalDataService), idempotencySharedUseCase);

    await relationalDataService.initialize();

    useFakeTimers(mockNow.getTime());
  });

  afterAll(async () => {
    await relationalDataService.destroy();
    jest.useRealTimers();
  });

  beforeEach(async () => {
    await relationalDataService.flush();
    jest.restoreAllMocks();
  });

  const testCases: SaveEventExpenseV2TestCase[] = [
    {
      name: 'должен успешно сохранить расход когда валюта события и расхода одинаковая',
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
      mockEventService: {
        isValidEvent: success(true),
      },
    },
    {
      name: 'должен успешно сохранить расход с конвертацией валюты',
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
      mockEventService: {
        isValidEvent: success(true),
      },
    },
    {
      name: 'должен успешно сохранить расход с кастомным курсом валюты',
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
          {userId: 'user-1', amount: 40, exchangedAmount: 50}, // Кастомный курс: 1.25 вместо автоматического 1.176
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
      mockEventService: {
        isValidEvent: success(true),
      },
    },
    {
      name: 'должен вернуть ошибку когда exchangedAmount указан не во всех splitInfo',
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
          {userId: 'user-1', amount: 40, exchangedAmount: 50}, // есть exchangedAmount
          {userId: 'user-2', amount: 60}, // нет exchangedAmount (undefined)
        ],
        pinCode: '1234',
        url: 'url',
      },
      output: error(new InconsistentExchangedAmountError()),
      relationalStateChanges: {},
      mockEventService: {
        isValidEvent: success(true),
      },
    },
    {
      name: 'повторный запрос с тем же idempotencyKey — возвращает кэш без создания расхода',
      initRelationalState: {},
      input: {
        eventId: 'event-1',
        currencyId: 'currency-usd',
        description: 'Lunch at restaurant',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: 'user-1', amount: 100}],
        pinCode: '1234',
        idempotencyKey: 'idempotency-key-1',
        url: SAVE_EXPENSE_V2_URL,
      },
      output: success({
        id: 'cached-expense-id',
        eventId: 'event-1',
        currencyId: 'currency-usd',
        description: 'Lunch at restaurant',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        isCustomRate: false,
        splitInformation: [{userId: 'user-1', amount: 100, exchangedAmount: 100}],
        createdAt: mockNow,
        updatedAt: mockNow,
      }),
      relationalStateChanges: {},
      mockEventService: {isValidEvent: success(true)},
      mockIdempotencyUseCase: {
        execute: success({
          id: 'cached-expense-id',
          eventId: 'event-1',
          currencyId: 'currency-usd',
          description: 'Lunch at restaurant',
          userWhoPaidId: 'user-1',
          expenseType: ExpenseType.Expense,
          isCustomRate: false,
          splitInformation: [{userId: 'user-1', amount: 100, exchangedAmount: 100}],
          createdAt: mockNow,
          updatedAt: mockNow,
        }),
      },
    },
    {
      name: 'должен вернуть ошибку когда события не существует',
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
      mockEventService: {
        isValidEvent: error(new EventNotFoundError()),
      },
    },
    {
      name: 'должен вернуть ошибку когда событие удалено',
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
      mockEventService: {
        isValidEvent: error(new EventDeletedError()),
      },
    },
    {
      name: 'должен вернуть ошибку когда pin код неверный',
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
      mockEventService: {
        isValidEvent: error(new InvalidPinCodeError()),
      },
    },
    {
      name: 'должен вернуть ошибку когда валюта не найдена',
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
      mockEventService: {
        isValidEvent: success(true),
      },
    },
    {
      name: 'должен вернуть ошибку когда курс валюты не найден',
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
      mockEventService: {
        isValidEvent: success(true),
      },
    },
  ];

  testCases.forEach((testCase) => {
    it(testCase.name, async () => {
      await prepareInitRelationalState({
        rDataService: relationalDataService,
        initState: testCase.initRelationalState,
      });

      jest.spyOn(eventService, 'isValidEvent').mockReturnValue(testCase.mockEventService.isValidEvent);

      if (testCase.mockIdempotencyUseCase) {
        const {execute} = testCase.mockIdempotencyUseCase;
        jest.spyOn(idempotencySharedUseCase, 'execute').mockReturnValue(Promise.resolve(execute));
      }

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
