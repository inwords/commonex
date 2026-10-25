import {randomUUID} from 'crypto';

import {Init1792934624547} from '../migrations/default/1792934624547-init';
import {createTestRelationalDataService} from '../src/test-support/db';

describe('Event protocol migration', () => {
  const dataService = createTestRelationalDataService();

  beforeAll(async () => {
    await dataService.initialize();
  });

  afterAll(async () => {
    await dataService.destroy();
  });

  it('preserves existing event data and defaults existing and legacy inserts to protocol one', async () => {
    const runner = dataService.dataSource.createQueryRunner();
    await runner.connect();
    await runner.startTransaction();
    try {
      const schema = `event_protocol_${randomUUID().replaceAll('-', '')}`;
      await runner.query(`CREATE SCHEMA "${schema}"`);
      await runner.query(`SET LOCAL search_path TO "${schema}"`);
      await runner.query(`CREATE TABLE event (
        id varchar PRIMARY KEY, name varchar NOT NULL, currency_id varchar NOT NULL,
        pin_code varchar NOT NULL, created_at timestamptz NOT NULL,
        updated_at timestamptz NOT NULL, deleted_at timestamptz
      )`);
      await runner.query(
        `INSERT INTO event VALUES ('legacy-event', 'Trip', 'USD', '1234', '2026-01-01', '2026-01-02', NULL)`,
      );
      const [original] = await runner.manager.query<Record<string, unknown>[]>('SELECT * FROM event');

      await new Init1792934624547().up(runner);
      await runner.query(`INSERT INTO event (id, name, currency_id, pin_code, created_at, updated_at, version)
        VALUES ('new-event', 'New trip', 'USD', '1234', '2026-01-01', '2026-01-02', 2)`);

      const [migrated] = await runner.manager.query<Record<string, unknown>[]>(
        "SELECT * FROM event WHERE id = 'legacy-event'",
      );
      expect(migrated).toEqual({...original, version: 1});
      await runner.query(`INSERT INTO event (id, name, currency_id, pin_code, created_at, updated_at)
        VALUES ('legacy-writer', 'Trip', 'USD', '1234', '2026-01-01', '2026-01-02')`);
      const versions = await runner.manager.query<{version: number}[]>('SELECT version FROM event ORDER BY id');
      expect(versions).toEqual([{version: 1}, {version: 1}, {version: 2}]);

      await new Init1792934624547().down(runner);
      const [rolledBack] = await runner.manager.query<Record<string, unknown>[]>(
        "SELECT * FROM event WHERE id = 'legacy-event'",
      );
      expect(rolledBack).toEqual(original);
    } finally {
      await runner.rollbackTransaction();
      await runner.release();
    }
  });
});
