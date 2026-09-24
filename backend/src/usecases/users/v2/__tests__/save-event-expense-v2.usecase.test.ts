import {error, isError, success} from '#packages/result';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {CurrencyCode} from '#domain/entities/currency.entity';
import {ExpenseType} from '#domain/entities/expense.entity';
import {
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
  EventDeletedError,
  EventNotFoundError,
  ExpenseAlreadyRevertedError,
  ExpenseCorrectionConflictError,
  ExpenseReferenceNotFoundError,
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
      name: 'preserves supplied exchanged amounts without marking a correction as custom rate',
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
        expenses: [
          {
            id: 'expense-original',
            eventId: 'event-1',
            currencyId: 'currency-eur',
            description: 'Original description',
            userWhoPaidId: 'user-1',
            expenseType: ExpenseType.Expense,
            splitInformation: [
              {userId: 'user-1', amount: 0.47, exchangedAmount: 0},
              {userId: 'user-2', amount: 8.49, exchangedAmount: 0.09},
            ],
            isCustomRate: false,
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        currencyId: 'currency-eur',
        description: 'Corrected description',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [
          {userId: 'user-1', amount: 0.47, exchangedAmount: 0},
          {userId: 'user-2', amount: 8.49, exchangedAmount: 0.09},
        ],
        isCustomRate: false,
        replacesExpenseId: 'expense-original',
        pinCode: '1234',
        url: 'url',
      },
      output: success({
        id: expect.any(String),
        eventId: 'event-1',
        currencyId: 'currency-eur',
        description: 'Corrected description',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [
          {userId: 'user-1', amount: 0.47, exchangedAmount: 0},
          {userId: 'user-2', amount: 8.49, exchangedAmount: 0.09},
        ],
        isCustomRate: false,
        replacesExpenseId: 'expense-original',
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
              description: 'Corrected description',
              userWhoPaidId: 'user-1',
              expenseType: ExpenseType.Expense,
              splitInformation: [
                {userId: 'user-1', amount: 0.47, exchangedAmount: 0},
                {userId: 'user-2', amount: 8.49, exchangedAmount: 0.09},
              ],
              isCustomRate: false,
              replacesExpenseId: 'expense-original',
              createdAt: expect.any(Date),
              updatedAt: expect.any(Date),
            },
          ],
        },
      },
    },
    {
      name: 'rejects supplied exchanged amounts marked non-custom for a non-correction',
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
        description: 'Invalid automatic-rate expense',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: 'user-1', amount: 10, exchangedAmount: 20}],
        isCustomRate: false,
        pinCode: '1234',
        url: 'url',
      },
      output: error(new InconsistentExchangedAmountError()),
      relationalStateChanges: {},
    },
    {
      name: 'saves a reversal linked to an expense in the same event',
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
        expenses: [
          {
            id: 'expense-original',
            eventId: 'event-1',
            currencyId: 'currency-usd',
            description: 'Original expense',
            userWhoPaidId: 'user-1',
            expenseType: ExpenseType.Expense,
            splitInformation: [{userId: 'user-2', amount: 100, exchangedAmount: 100}],
            isCustomRate: false,
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        currencyId: 'currency-usd',
        description: 'Revert original expense',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Refund,
        splitInformation: [{userId: 'user-2', amount: -100}],
        revertsExpenseId: 'expense-original',
        pinCode: '1234',
        url: 'url',
      },
      output: success({
        id: expect.any(String),
        eventId: 'event-1',
        currencyId: 'currency-usd',
        description: 'Revert original expense',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Refund,
        splitInformation: [{userId: 'user-2', amount: -100, exchangedAmount: -100}],
        revertsExpenseId: 'expense-original',
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
              description: 'Revert original expense',
              userWhoPaidId: 'user-1',
              expenseType: ExpenseType.Refund,
              splitInformation: [{userId: 'user-2', amount: -100, exchangedAmount: -100}],
              revertsExpenseId: 'expense-original',
              isCustomRate: false,
              createdAt: expect.any(Date),
              updatedAt: expect.any(Date),
            },
          ],
        },
      },
    },
    {
      name: 'returns ExpenseCorrectionConflictError when an expense both reverts and replaces another expense',
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
        description: 'Invalid correction',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: 'user-1', amount: 100}],
        revertsExpenseId: 'expense-original',
        replacesExpenseId: 'expense-original',
        pinCode: '1234',
        url: 'url',
      },
      output: error(new ExpenseCorrectionConflictError()),
      relationalStateChanges: {},
    },
    {
      name: 'returns ExpenseReferenceNotFoundError when the reverted expense is missing',
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
        description: 'Revert missing expense',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Refund,
        splitInformation: [{userId: 'user-1', amount: -100}],
        revertsExpenseId: 'missing-expense',
        pinCode: '1234',
        url: 'url',
      },
      output: error(new ExpenseReferenceNotFoundError()),
      relationalStateChanges: {},
    },
    {
      name: 'returns ExpenseReferenceNotFoundError when the reverted expense belongs to another event',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Current Event',
            currencyId: 'currency-usd',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
          {
            id: 'event-2',
            name: 'Other Event',
            currencyId: 'currency-usd',
            pinCode: '5678',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
        expenses: [
          {
            id: 'expense-other-event',
            eventId: 'event-2',
            currencyId: 'currency-usd',
            description: 'Other event expense',
            userWhoPaidId: 'user-1',
            expenseType: ExpenseType.Expense,
            splitInformation: [{userId: 'user-1', amount: 100, exchangedAmount: 100}],
            isCustomRate: false,
            createdAt: new Date('2023-01-02T00:00:00Z'),
            updatedAt: new Date('2023-01-02T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        currencyId: 'currency-usd',
        description: 'Cross-event revert',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Refund,
        splitInformation: [{userId: 'user-1', amount: -100}],
        revertsExpenseId: 'expense-other-event',
        pinCode: '1234',
        url: 'url',
      },
      output: error(new ExpenseReferenceNotFoundError()),
      relationalStateChanges: {},
    },
    {
      name: 'returns ExpenseReferenceNotFoundError when the replaced expense belongs to another event',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Current Event',
            currencyId: 'currency-usd',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
          {
            id: 'event-2',
            name: 'Other Event',
            currencyId: 'currency-usd',
            pinCode: '5678',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
        expenses: [
          {
            id: 'expense-other-event',
            eventId: 'event-2',
            currencyId: 'currency-usd',
            description: 'Other event expense',
            userWhoPaidId: 'user-1',
            expenseType: ExpenseType.Expense,
            splitInformation: [{userId: 'user-1', amount: 100, exchangedAmount: 100}],
            isCustomRate: false,
            createdAt: new Date('2023-01-02T00:00:00Z'),
            updatedAt: new Date('2023-01-02T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        currencyId: 'currency-usd',
        description: 'Cross-event replacement',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: 'user-1', amount: 100}],
        replacesExpenseId: 'expense-other-event',
        pinCode: '1234',
        url: 'url',
      },
      output: error(new ExpenseReferenceNotFoundError()),
      relationalStateChanges: {},
    },
    {
      name: 'returns ExpenseAlreadyRevertedError when another expense already reverts the original',
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
        expenses: [
          {
            id: 'expense-original',
            eventId: 'event-1',
            currencyId: 'currency-usd',
            description: 'Original expense',
            userWhoPaidId: 'user-1',
            expenseType: ExpenseType.Expense,
            splitInformation: [{userId: 'user-2', amount: 100, exchangedAmount: 100}],
            isCustomRate: false,
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
          {
            id: 'expense-revert',
            eventId: 'event-1',
            currencyId: 'currency-usd',
            description: 'Existing revert',
            userWhoPaidId: 'user-2',
            expenseType: ExpenseType.Refund,
            splitInformation: [{userId: 'user-2', amount: -100, exchangedAmount: -100}],
            revertsExpenseId: 'expense-original',
            isCustomRate: false,
            createdAt: new Date('2023-01-02T00:00:00Z'),
            updatedAt: new Date('2023-01-02T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        currencyId: 'currency-usd',
        description: 'Duplicate revert',
        userWhoPaidId: 'user-2',
        expenseType: ExpenseType.Refund,
        splitInformation: [{userId: 'user-2', amount: -100}],
        revertsExpenseId: 'expense-original',
        pinCode: '1234',
        url: 'url',
      },
      output: error(new ExpenseAlreadyRevertedError()),
      relationalStateChanges: {},
    },
    {
      name: 'returns ExpenseCorrectionConflictError when another expense already replaces the original',
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
        expenses: [
          {
            id: 'expense-original',
            eventId: 'event-1',
            currencyId: 'currency-usd',
            description: 'Original expense',
            userWhoPaidId: 'user-1',
            expenseType: ExpenseType.Expense,
            splitInformation: [{userId: 'user-2', amount: 100, exchangedAmount: 100}],
            isCustomRate: false,
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
          {
            id: 'expense-replacement',
            eventId: 'event-1',
            currencyId: 'currency-usd',
            description: 'Existing replacement',
            userWhoPaidId: 'user-1',
            expenseType: ExpenseType.Expense,
            splitInformation: [{userId: 'user-2', amount: 120, exchangedAmount: 120}],
            replacesExpenseId: 'expense-original',
            isCustomRate: false,
            createdAt: new Date('2023-01-02T00:00:00Z'),
            updatedAt: new Date('2023-01-02T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        currencyId: 'currency-usd',
        description: 'Second replacement',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: 'user-2', amount: 130}],
        replacesExpenseId: 'expense-original',
        pinCode: '1234',
        url: 'url',
      },
      output: error(new ExpenseCorrectionConflictError()),
      relationalStateChanges: {},
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
            {
              id: expect.any(String),
              eventId: 'event-1',
              currencyId: 'currency-eur',
              description: 'Old dinner',
              userWhoPaidId: 'user-1',
              expenseType: ExpenseType.Expense,
              splitInformation: [{userId: 'user-1', amount: 10, exchangedAmount: 20}],
              isCustomRate: false,
              createdAt: new Date('2025-12-15T18:00:00Z'),
              updatedAt: expect.any(Date),
            },
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

  it('persists canonical reversals without rates and supports reversing the reversal', async () => {
    const original = {
      id: 'original',
      eventId: 'event-1',
      currencyId: 'currency-eur',
      description: 'Original',
      userWhoPaidId: 'user-1',
      expenseType: ExpenseType.Expense,
      splitInformation: [
        {userId: 'user-2', amount: 10, exchangedAmount: 12.5},
        {userId: 'user-2', amount: 10, exchangedAmount: 12.51},
      ],
      isCustomRate: false,
      createdAt: new Date('2025-01-01'),
      updatedAt: new Date('2025-01-01'),
    };
    await prepareInitRelationalState({
      rDataService: relationalDataService,
      initState: {
        events: [
          {
            id: 'event-1',
            name: 'Trip',
            currencyId: 'currency-usd',
            pinCode: '1234',
            createdAt: mockNow,
            updatedAt: mockNow,
            deletedAt: null,
          },
        ],
        expenses: [original],
      },
    });
    const input = {
      eventId: 'event-1',
      pinCode: '1234',
      description: 'Undo',
      revertsExpenseId: original.id,
      url: SAVE_EXPENSE_V2_URL,
    };
    const result = await useCase.execute({
      ...input,
      currencyId: 'nonexistent',
      userWhoPaidId: 'wrong-user',
      expenseType: ExpenseType.Expense,
      isCustomRate: true,
      splitInformation: [{userId: 'wrong-user', amount: 999, exchangedAmount: -11}],
    });
    expect(result).toEqual(
      success({
        ...original,
        id: expect.any(String),
        description: 'Undo',
        expenseType: ExpenseType.Refund,
        revertsExpenseId: original.id,
        createdAt: mockNow,
        updatedAt: mockNow,
        splitInformation: [
          {userId: 'user-2', amount: -10, exchangedAmount: -12.5},
          {userId: 'user-2', amount: -10, exchangedAmount: -12.51},
        ],
      }),
    );
    if (isError(result)) throw new Error('Expected reversal success');
    const [stored] = await relationalDataService.expense.findById(result.value.id);
    expect(stored).toMatchObject(result.value);

    const restored = await useCase.execute({
      ...input,
      currencyId: original.currencyId,
      userWhoPaidId: original.userWhoPaidId,
      expenseType: ExpenseType.Expense,
      splitInformation: original.splitInformation,
      revertsExpenseId: result.value.id,
      description: 'Restore',
    });
    expect(restored).toEqual(
      success({
        ...original,
        id: expect.any(String),
        description: 'Restore',
        revertsExpenseId: result.value.id,
        createdAt: mockNow,
        updatedAt: mockNow,
      }),
    );
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
