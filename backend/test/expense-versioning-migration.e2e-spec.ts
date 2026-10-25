import {randomUUID} from 'crypto';

import {ExpenseVersioning1792099053000} from '../migrations/default/1792099053000-expense-versioning';
import {createTestRelationalDataService} from '../src/test-support/db';

describe('Expense versioning migration', () => {
  const dataService = createTestRelationalDataService();

  beforeAll(async () => {
    await dataService.initialize();
  });

  afterAll(async () => {
    await dataService.destroy();
  });

  it('preserves a legacy financial snapshot as version one and permits a new revision', async () => {
    const runner = dataService.dataSource.createQueryRunner();
    await runner.connect();
    await runner.startTransaction();
    try {
      const schema = `expense_migration_${randomUUID().replaceAll('-', '')}`;
      await runner.query(`CREATE SCHEMA "${schema}"`);
      await runner.query(`SET LOCAL search_path TO "${schema}"`);
      await runner.query(`CREATE TABLE expense (
        id varchar CONSTRAINT pk__expense__id PRIMARY KEY,
        description varchar NOT NULL,
        user_who_paid_id varchar NOT NULL,
        currency_id varchar NOT NULL,
        event_id varchar NOT NULL,
        expense_type varchar NOT NULL,
        split_information jsonb NOT NULL,
        is_custom_rate boolean NOT NULL,
        created_at timestamptz NOT NULL,
        updated_at timestamptz NOT NULL
      )`);
      await runner.query('CREATE INDEX idx__expense__event_id ON expense (event_id)');
      await runner.query(`INSERT INTO expense VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`, [
        'legacy-expense',
        'Dinner',
        'payer',
        'EUR',
        'event',
        'expense',
        JSON.stringify([{userId: 'participant', amount: 100, exchangedAmount: 125.75}]),
        true,
        new Date('2026-10-01T12:00:00Z'),
        new Date('2026-10-02T12:00:00Z'),
      ]);
      const [original] = await runner.manager.query<Record<string, unknown>[]>('SELECT * FROM expense');

      await new ExpenseVersioning1792099053000().up(runner);

      const [migrated] = await runner.manager.query<Record<string, unknown>[]>('SELECT * FROM expense');
      expect(migrated).toEqual({...original, version: 1});
      await runner.query(`INSERT INTO expense
        SELECT id, description, user_who_paid_id, currency_id, event_id, expense_type,
               split_information, is_custom_rate, created_at, updated_at, 2
        FROM expense WHERE version = 1`);
      const versions = await runner.manager.query<{version: number}[]>('SELECT version FROM expense ORDER BY version');
      expect(versions).toEqual([{version: 1}, {version: 2}]);
    } finally {
      await runner.rollbackTransaction();
      await runner.release();
    }
  });
});
