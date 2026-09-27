import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';

import {ExpenseType} from '#domain/entities/expense.entity';

import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {createTestRelationalDataService, truncateAllTables} from '#test-support/db';

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value != null;

const findTopEventsSql = (value: unknown): string | undefined => {
  if (Array.isArray(value)) {
    for (const item of value) {
      const result = findTopEventsSql(item);
      if (result != null) return result;
    }
    return undefined;
  }

  if (!isRecord(value)) return undefined;

  if (value['title'] === 'Top 20 Most Active Events' && Array.isArray(value['targets'])) {
    const target = value['targets'].find((candidate) => isRecord(candidate) && typeof candidate['rawSql'] === 'string');
    if (isRecord(target) && typeof target['rawSql'] === 'string') return target['rawSql'];
  }

  for (const child of Object.values(value)) {
    const result = findTopEventsSql(child);
    if (result != null) return result;
  }

  return undefined;
};

describe('Grafana expenses analytics dashboard', () => {
  let relationalDataService: RelationalDataService;

  beforeAll(async () => {
    relationalDataService = createTestRelationalDataService();
    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
  });

  it('reports Top Events counts without multiplying expenses by users', async () => {
    const createdAt = new Date('2026-01-01T00:00:00Z');
    const updatedAt = new Date('2026-01-02T00:00:00Z');

    await relationalDataService.event.insert({
      id: 'event-with-data',
      name: 'Event with data',
      currencyId: 'USD',
      pinCode: '1111',
      createdAt,
      updatedAt,
      deletedAt: null,
    });
    await relationalDataService.event.insert({
      id: 'empty-event',
      name: 'Empty event',
      currencyId: 'USD',
      pinCode: '2222',
      createdAt,
      updatedAt,
      deletedAt: null,
    });

    await relationalDataService.userInfo.insert(
      ['user-1', 'user-2', 'user-3'].map((id) => ({id, name: id, eventId: 'event-with-data', createdAt, updatedAt})),
    );

    for (const expense of [
      {id: 'expense-1', isCustomRate: true},
      {id: 'expense-2', isCustomRate: false},
    ]) {
      await relationalDataService.expense.insert({
        ...expense,
        description: expense.id,
        userWhoPaidId: 'user-1',
        currencyId: 'USD',
        eventId: 'event-with-data',
        expenseType: ExpenseType.Expense,
        splitInformation: [],
        createdAt,
        updatedAt,
      });
    }

    const dashboardPath = resolve(process.cwd(), '../infra/grafana/sync/expenses-analytics.json');
    const dashboard = JSON.parse(readFileSync(dashboardPath, 'utf8')) as unknown;
    const rawSql = findTopEventsSql(dashboard);
    expect(rawSql).toBeDefined();
    if (rawSql == null) throw new Error('Top Events SQL is missing');

    const rows = await relationalDataService.dataSource.query<Record<string, string | null>[]>(
      rawSql.replaceAll('${postgres_schema}', 'public'),
    );

    expect(rows).toEqual([
      expect.objectContaining({'Event Name': 'Event with data', Expenses: '2', Users: '3', 'Custom Rates': '1'}),
      expect.objectContaining({'Event Name': 'Empty event', Expenses: '0', Users: '0', 'Custom Rates': '0'}),
    ]);
  });
});
