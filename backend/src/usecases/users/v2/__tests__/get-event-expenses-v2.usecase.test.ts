import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {GetEventExpensesV2UseCase} from '../get-event-expenses-v2.usecase';
import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {TestCase, prepareInitRelationalState} from '../../../__tests__/test-helpers';
import {Result, error, success} from '#packages/result';
import {EventNotFoundError, EventDeletedError, InvalidPinCodeError} from '#domain/errors/errors';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {EventService} from '#frameworks/event-service/event-service';
import {ExpenseType} from '#domain/entities/expense.entity';

type GetEventExpensesV2TestCase = TestCase<GetEventExpensesV2UseCase> & {
  mockEventService: {
    isValidEvent: Result<boolean, EventNotFoundError | EventDeletedError | InvalidPinCodeError>;
  };
};

describe('GetEventExpensesV2UseCase', () => {
  let relationalDataService: RelationalDataServiceAbstract;
  let useCase: GetEventExpensesV2UseCase;
  let eventService: EventServiceAbstract;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    eventService = new EventService();
    useCase = new GetEventExpensesV2UseCase(relationalDataService, eventService);

    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await relationalDataService.flush();
    jest.clearAllMocks();
  });

  const testCases: GetEventExpensesV2TestCase[] = [
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
        pinCode: '1234',
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
        isValidEvent: success(true),
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
        pinCode: '1234',
      },
      output: success([]),
      mockEventService: {
        isValidEvent: success(true),
      },
    },
    {
      name: 'должен вернуть ошибку когда события не существует',
      initRelationalState: {},
      input: {
        eventId: 'non-existent',
        pinCode: '1234',
      },
      output: error(new EventNotFoundError()),
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
      mockEventService: {
        isValidEvent: error(new InvalidPinCodeError()),
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

      const result = await useCase.execute(testCase.input);

      expect(result).toEqual(testCase.output);
    });
  });
});
