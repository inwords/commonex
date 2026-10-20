import {ExpenseType, IExpense} from '#domain/entities/expense.entity';

import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {truncateAllTables} from '#test-support/db';

describe('ExpenseRepository', () => {
  let relationalDataService: RelationalDataService;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: true,
    });
    await relationalDataService.initialize();
    // Clear the database before running the tests
    await truncateAllTables(relationalDataService.dataSource);
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
  });

  describe('insert', () => {
    it('should insert expense correctly', async () => {
      const expense = {
        id: 'expense-1',
        version: 1,
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
      // First insert test data
      const expense = {
        id: 'expense-1',
        version: 1,
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

      // Now look up expenses by event id
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
          version: 1,
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
          version: 1,
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
          version: 1,
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
          version: 1,
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
          version: 1,
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

  describe('versions', () => {
    const original: IExpense = {
      id: 'expense-1',
      version: 1,
      eventId: 'event-1',
      description: 'Dinner',
      userWhoPaidId: 'user-1',
      currencyId: 'currency-1',
      expenseType: ExpenseType.Expense,
      isCustomRate: false,
      splitInformation: [{userId: 'user-2', amount: 100, exchangedAmount: 100}],
      createdAt: new Date('2023-01-01T00:00:00Z'),
      updatedAt: new Date('2023-01-01T00:00:00Z'),
    };

    it('returns only latest snapshots while retaining ordered event-scoped history', async () => {
      const revised = {
        ...original,
        version: 2,
        description: 'Corrected dinner',
        splitInformation: [{userId: 'user-2', amount: 80, exchangedAmount: 80}],
      };
      const other = {...original, id: 'expense-2'};
      await relationalDataService.expense.insert([revised, original, other]);

      const [latest] = await relationalDataService.expense.findByEventId(original.eventId);
      const [limited] = await relationalDataService.expense.findAll({limit: 1});
      const [found] = await relationalDataService.expense.findLatestById(original.eventId, original.id);
      const [history] = await relationalDataService.expense.findHistoryById(original.eventId, original.id);
      const [foreignLatest] = await relationalDataService.expense.findLatestById('other-event', original.id);
      const [foreignHistory] = await relationalDataService.expense.findHistoryById('other-event', original.id);

      expect(latest).toEqual([revised, other]);
      expect(limited).toEqual([revised]);
      expect(found).toEqual(revised);
      expect(history).toEqual([original, revised]);
      expect(foreignLatest).toBeNull();
      expect(foreignHistory).toEqual([]);
    });

    it('rejects duplicate revisions in the database', async () => {
      await relationalDataService.expense.insert(original);

      await expect(relationalDataService.expense.insert(original)).rejects.toMatchObject({
        driverError: {code: '23505'},
      });
      const [history] = await relationalDataService.expense.findHistoryById(original.eventId, original.id);
      expect(history).toEqual([original]);
    });
  });
});
