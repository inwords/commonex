import {randomUUID} from 'crypto';

import {DataSource, QueryRunner} from 'typeorm';

import {appDbConfig, createTypeormConfigDefault} from '#frameworks/relational-data-service/postgres/config';

import {Init1791726271060} from '../migrations/default/1791726271060-init';

describe('Share token uniqueness migration', () => {
  let dataSource: DataSource;
  let runner: QueryRunner;
  let schema: string;

  beforeEach(async () => {
    schema = `share_token_test_${randomUUID().replaceAll('-', '')}`;
    dataSource = new DataSource(createTypeormConfigDefault(appDbConfig));
    await dataSource.initialize();
    runner = dataSource.createQueryRunner();
    await runner.connect();
    await runner.query(`CREATE SCHEMA "${schema}"`);
    await runner.query(`SET search_path TO "${schema}"`);
    await runner.query(`CREATE TABLE event_share_token (
      token varchar PRIMARY KEY, event_id varchar NOT NULL,
      expires_at timestamptz NOT NULL, created_at timestamptz NOT NULL
    )`);
  });

  afterEach(async () => {
    if (runner.isTransactionActive) {
      await runner.rollbackTransaction();
    }
    await runner.query(`DROP SCHEMA "${schema}" CASCADE`);
    await runner.release();
    await dataSource.destroy();
  });

  it('keeps the latest expiry per event and rejects duplicates after migration', async () => {
    await runner.query(`INSERT INTO event_share_token VALUES
      ('older', 'event-1', '2027-01-01', '2026-01-01'),
      ('latest', 'event-1', '2028-01-01', '2025-01-01'),
      ('tie-old', 'event-2', '2028-01-01', '2025-01-01'),
      ('tie-b', 'event-2', '2028-01-01', '2026-01-01'),
      ('tie-a', 'event-2', '2028-01-01', '2026-01-01'),
      ('expired', 'event-3', '2020-01-01', '2019-01-01')`);

    const migration = new Init1791726271060();
    await runner.startTransaction();
    await migration.up(runner);
    await runner.commitTransaction();

    await expect(runner.query('SELECT token FROM event_share_token ORDER BY event_id')).resolves.toEqual([
      {token: 'latest'},
      {token: 'tie-a'},
      {token: 'expired'},
    ]);
    await expect(
      runner.query("INSERT INTO event_share_token VALUES ('duplicate', 'event-1', '2029-01-01', '2026-01-01')"),
    ).rejects.toMatchObject({driverError: {code: '23505'}});

    await migration.down(runner);
    await runner.query("INSERT INTO event_share_token VALUES ('duplicate', 'event-1', '2029-01-01', '2026-01-01')");
    await expect(runner.query('SELECT COUNT(*)::integer AS count FROM event_share_token')).resolves.toEqual([
      {count: 4},
    ]);
  });
});
