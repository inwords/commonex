import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {SaveEventExpenseUseCase} from '../save-event-expense.usecase';
import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {
  TestCase,
  prepareInitRelationalState,
  validateRelationalStateChanges,
  useFakeTimers,
} from '../../__tests__/test-helpers';
import {Result, error, success} from '#packages/result';
import {
  EventNotFoundError,
  EventDeletedError,
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
} from '#domain/errors/errors';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {EventService} from '#frameworks/event-service/event-service';
import {CurrencyCode} from '#domain/entities/currency.entity';
import {ExpenseType} from '#domain/entities/expense.entity';

type SaveEventExpenseTestCase = TestCase<SaveEventExpenseUseCase> & {
  mockEventService?: {
    isEventExists?: boolean;
    isEventNotDeleted?: Result<boolean, EventDeletedError>;
  };
};

describe('SaveEventExpenseUseCase', () => {
  let relationalDataService: RelationalDataServiceAbstract;
  let useCase: SaveEventExpenseUseCase;
  let eventService: EventServiceAbstract;

  const mockNow = new Date('2026-01-01T00:00:00.000Z');

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    eventService = new EventService();
    useCase = new SaveEventExpenseUseCase(relationalDataService, eventService);

    await relationalDataService.initialize();

    useFakeTimers(mockNow.getTime());
  });

  afterAll(async () => {
    await relationalDataService.destroy();
    jest.useRealTimers();
  });

  beforeEach(async () => {
    await relationalDataService.flush();
    jest.clearAllMocks();
  });

  const testCases: SaveEventExpenseTestCase[] = [
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
        isCustomRate: false,
        splitInformation: [
          {userId: 'user-1', amount: 40, exchangedAmount: 0},
          {userId: 'user-2', amount: 60, exchangedAmount: 0},
        ],
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
      mockEventService: {
        isEventExists: true,
        isEventNotDeleted: success(true),
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
        isCustomRate: false,
        splitInformation: [
          {userId: 'user-1', amount: 40, exchangedAmount: 0},
          {userId: 'user-2', amount: 60, exchangedAmount: 0},
        ],
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
      mockEventService: {
        isEventExists: true,
        isEventNotDeleted: success(true),
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
        isCustomRate: false,
        splitInformation: [{userId: 'user-1', amount: 100, exchangedAmount: 0}],
      },
      output: error(new EventNotFoundError()),
      relationalStateChanges: {},
      mockEventService: {
        isEventExists: false,
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
        isCustomRate: false,
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: 'user-1', amount: 100, exchangedAmount: 0}],
      },
      output: error(new EventDeletedError()),
      relationalStateChanges: {},
      mockEventService: {
        isEventExists: true,
        isEventNotDeleted: error(new EventDeletedError()),
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
        isCustomRate: false,
        description: 'Lunch',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: 'user-1', amount: 100, exchangedAmount: 0}],
      },
      output: error(new CurrencyNotFoundError()),
      relationalStateChanges: {},
      mockEventService: {
        isEventExists: true,
        isEventNotDeleted: success(true),
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
        isCustomRate: false,
        currencyId: 'currency-eur',
        description: 'Lunch',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: 'user-1', amount: 100, exchangedAmount: 0}],
      },
      output: error(new CurrencyRateNotFoundError()),
      relationalStateChanges: {},
      mockEventService: {
        isEventExists: true,
        isEventNotDeleted: success(true),
      },
    },
  ];

  testCases.forEach((testCase) => {
    it(testCase.name, async () => {
      await prepareInitRelationalState({
        rDataService: relationalDataService,
        initState: testCase.initRelationalState,
      });

      if (testCase.mockEventService) {
        if (testCase.mockEventService.isEventExists !== undefined) {
          jest.spyOn(eventService, 'isEventExists').mockReturnValue(testCase.mockEventService.isEventExists);
        }
        if (testCase.mockEventService.isEventNotDeleted) {
          jest.spyOn(eventService, 'isEventNotDeleted').mockReturnValue(testCase.mockEventService.isEventNotDeleted);
        }
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
