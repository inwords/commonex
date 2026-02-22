import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {ExpenseType} from '#domain/entities/expense.entity';

describe('ExpenseRepository', () => {
  let relationalDataService: RelationalDataService;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: true,
    });
    await relationalDataService.initialize();
    // Очищаем базу данных перед запуском тестов
    await relationalDataService.flush();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await relationalDataService.flush();
  });

  describe('insert', () => {
    it('should insert expense correctly', async () => {
      const expense = {
        id: 'expense-1',
        description: 'Dinner',
        userWhoPaidId: 'user-1',
        currencyId: 'currency-1',
        eventId: 'event-1',
        expenseType: ExpenseType.Expense,
        isCustomRate: false,
        splitInformation: [
          {
            userId: 'user-1',
            amount: 50,
            exchangedAmount: 50,
          },
          {
            userId: 'user-2',
            amount: 50,
            exchangedAmount: 50,
          },
        ],
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
      };

      const [, queryDetails] = await relationalDataService.expense.insert(expense);
      const [foundExpenses] = await relationalDataService.expense.findByEventId('event-1');

      expect(foundExpenses).toMatchObject([expense]);
      expect(queryDetails).toMatchSnapshot();
    });
  });

  describe('findByEventId', () => {
    it('should find expenses by event id', async () => {
      // Сначала вставляем тестовые данные
      const expense = {
        id: 'expense-1',
        description: 'Dinner',
        userWhoPaidId: 'user-1',
        currencyId: 'currency-1',
        eventId: 'event-1',
        expenseType: ExpenseType.Expense,
        isCustomRate: false,
        splitInformation: [
          {
            userId: 'user-1',
            amount: 50,
            exchangedAmount: 50,
          },
          {
            userId: 'user-2',
            amount: 50,
            exchangedAmount: 50,
          },
        ],
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
      };

      await relationalDataService.expense.insert(expense);

      // Теперь ищем расходы по event id
      const [result, queryDetails] = await relationalDataService.expense.findByEventId('event-1');

      expect(result).toMatchObject([expense]);
      expect(queryDetails).toMatchSnapshot();
    });

    it('should return empty array for non-existent event', async () => {
      const [result, queryDetails] = await relationalDataService.expense.findByEventId('non-existent');

      expect(result).toEqual([]);
      expect(queryDetails).toMatchSnapshot();
    });
  });

  describe('findAll', () => {
    it('should find all expenses with limit', async () => {
      const expenses = [
        {
          id: 'expense-1',
          description: 'Dinner',
          userWhoPaidId: 'user-1',
          currencyId: 'currency-1',
          eventId: 'event-1',
          expenseType: ExpenseType.Expense,
          isCustomRate: false,
          splitInformation: [
            {
              userId: 'user-1',
              amount: 50,
              exchangedAmount: 50,
            },
            {
              userId: 'user-2',
              amount: 50,
              exchangedAmount: 50,
            },
          ],
          createdAt: new Date('2023-01-01T00:00:00Z'),
          updatedAt: new Date('2023-01-01T00:00:00Z'),
        },
        {
          id: 'expense-2',
          description: 'Lunch',
          userWhoPaidId: 'user-2',
          currencyId: 'currency-1',
          eventId: 'event-1',
          expenseType: ExpenseType.Expense,
          isCustomRate: false,
          splitInformation: [
            {
              userId: 'user-1',
              amount: 30,
              exchangedAmount: 30,
            },
            {
              userId: 'user-2',
              amount: 30,
              exchangedAmount: 30,
            },
          ],
          createdAt: new Date('2023-01-02T00:00:00Z'),
          updatedAt: new Date('2023-01-02T00:00:00Z'),
        },
      ];

      await relationalDataService.expense.insert(expenses);

      const [result, queryDetails] = await relationalDataService.expense.findAll({limit: 10});

      expect(result).toMatchObject(expenses);
      expect(queryDetails).toMatchSnapshot();
    });

    it('should respect limit parameter', async () => {
      const expenses = [
        {
          id: 'expense-1',
          description: 'Dinner',
          userWhoPaidId: 'user-1',
          currencyId: 'currency-1',
          eventId: 'event-1',
          expenseType: ExpenseType.Expense,
          splitInformation: [
            {
              userId: 'user-1',
              amount: 50,
              exchangedAmount: 50,
            },
          ],
          isCustomRate: false,

          createdAt: new Date('2023-01-01T00:00:00Z'),
          updatedAt: new Date('2023-01-01T00:00:00Z'),
        },
        {
          id: 'expense-2',
          description: 'Lunch',
          userWhoPaidId: 'user-2',
          currencyId: 'currency-1',
          eventId: 'event-1',
          expenseType: ExpenseType.Expense,
          splitInformation: [
            {
              userId: 'user-1',
              amount: 30,
              exchangedAmount: 30,
            },
          ],
          isCustomRate: false,

          createdAt: new Date('2023-01-02T00:00:00Z'),
          updatedAt: new Date('2023-01-02T00:00:00Z'),
        },
        {
          id: 'expense-3',
          description: 'Breakfast',
          userWhoPaidId: 'user-1',
          currencyId: 'currency-1',
          eventId: 'event-1',
          expenseType: ExpenseType.Expense,
          splitInformation: [
            {
              userId: 'user-1',
              amount: 20,
              exchangedAmount: 20,
            },
          ],
          isCustomRate: false,

          createdAt: new Date('2023-01-03T00:00:00Z'),
          updatedAt: new Date('2023-01-03T00:00:00Z'),
        },
      ];

      await relationalDataService.expense.insert(expenses);

      const [result, queryDetails] = await relationalDataService.expense.findAll({limit: 2});

      expect(result).toHaveLength(2);
      expect(queryDetails).toMatchSnapshot();
    });
  });
});
