import {Result, error, success} from '#packages/result';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {ExpenseType} from '#domain/entities/expense.entity';
import {EventDeletedError, EventNotFoundError} from '#domain/errors/errors';

import {EventService} from '#frameworks/event-service/event-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {truncateAllTables} from '#test-support/db';
import {TestCase, prepareInitRelationalState} from '#test-support/relational-state';

import {GetEventExpensesUseCase} from '../get-event-expenses.usecase';

type GetEventExpensesTestCase = TestCase<GetEventExpensesUseCase> & {
  mockEventService?: {
    isEventExists?: boolean;
    isEventNotDeleted?: Result<boolean, EventDeletedError>;
  };
};

describe('GetEventExpensesUseCase', () => {
  let relationalDataService: RelationalDataService;
  let useCase: GetEventExpensesUseCase;
  let eventService: EventServiceAbstract;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    eventService = new EventService();
    useCase = new GetEventExpensesUseCase(relationalDataService, eventService);

    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
    jest.clearAllMocks();
  });

  const testCases: GetEventExpensesTestCase[] = [
    {
      name: 'должен вернуть расходы события',
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
        expenses: [
          {
            id: 'expense-1',
            eventId: 'event-1',
            currencyId: 'currency-1',
            description: 'Lunch',
            userWhoPaidId: 'user-1',
            expenseType: ExpenseType.Expense,
            splitInformation: [
              {userId: 'user-1', amount: 50, exchangedAmount: 50},
              {userId: 'user-2', amount: 50, exchangedAmount: 50},
            ],
            isCustomRate: false,
            createdAt: new Date('2023-01-02T00:00:00Z'),
            updatedAt: new Date('2023-01-02T00:00:00Z'),
          },
          {
            id: 'expense-2',
            eventId: 'event-1',
            currencyId: 'currency-1',
            description: 'Dinner',
            userWhoPaidId: 'user-2',
            expenseType: ExpenseType.Expense,
            splitInformation: [
              {userId: 'user-1', amount: 100, exchangedAmount: 100},
              {userId: 'user-2', amount: 100, exchangedAmount: 100},
            ],
            isCustomRate: false,
            createdAt: new Date('2023-01-03T00:00:00Z'),
            updatedAt: new Date('2023-01-03T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
      },
      output: success([
        {
          id: 'expense-1',
          eventId: 'event-1',
          currencyId: 'currency-1',
          description: 'Lunch',
          userWhoPaidId: 'user-1',
          expenseType: ExpenseType.Expense,
          splitInformation: [
            {userId: 'user-1', amount: 50, exchangedAmount: 50},
            {userId: 'user-2', amount: 50, exchangedAmount: 50},
          ],
          isCustomRate: false,
          createdAt: new Date('2023-01-02T00:00:00Z'),
          updatedAt: new Date('2023-01-02T00:00:00Z'),
        },
        {
          id: 'expense-2',
          eventId: 'event-1',
          currencyId: 'currency-1',
          description: 'Dinner',
          userWhoPaidId: 'user-2',
          expenseType: ExpenseType.Expense,
          splitInformation: [
            {userId: 'user-1', amount: 100, exchangedAmount: 100},
            {userId: 'user-2', amount: 100, exchangedAmount: 100},
          ],
          isCustomRate: false,
          createdAt: new Date('2023-01-03T00:00:00Z'),
          updatedAt: new Date('2023-01-03T00:00:00Z'),
        },
      ]),
      mockEventService: {
        isEventExists: true,
        isEventNotDeleted: success(true),
      },
    },
    {
      name: 'должен вернуть пустой массив когда расходов нет',
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
      },
      output: success([]),
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
      },
      output: error(new EventNotFoundError()),
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
      },
      output: error(new EventDeletedError()),
      mockEventService: {
        isEventExists: true,
        isEventNotDeleted: error(new EventDeletedError()),
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
    });
  });
});
