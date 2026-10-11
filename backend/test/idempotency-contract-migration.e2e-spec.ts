import {randomUUID} from 'crypto';

import {DataSource, QueryRunner} from 'typeorm';

import {appDbConfig, createTypeormConfigDefault} from '#frameworks/relational-data-service/postgres/config';

import {IdempotencyContract1791727440000} from '../migrations/default/1791727440000-IdempotencyContract';

describe('Idempotency contract migration', () => {
  let dataSource: DataSource;
  let runner: QueryRunner;
  let schema: string;
  const migration = new IdempotencyContract1791727440000();

  beforeEach(async () => {
    schema = `idempotency_contract_${randomUUID().replaceAll('-', '')}`;
    dataSource = new DataSource(createTypeormConfigDefault(appDbConfig));
    await dataSource.initialize();
    runner = dataSource.createQueryRunner();
    await runner.connect();
    await runner.query(`CREATE SCHEMA "${schema}"`);
    await runner.query(`SET search_path TO "${schema}"`);
    await runner.query(`CREATE TABLE idempotency_keys (
      key varchar PRIMARY KEY, url varchar, request_hash varchar, response jsonb, status_code integer,
      operation_id varchar, request_hash_v2 varchar, response_v2 jsonb, response_version integer,
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

  const migrate = async (): Promise<void> => {
    await runner.startTransaction();
    try {
      await migration.up(runner);
      await runner.commitTransaction();
    } catch (cause) {
      await runner.rollbackTransaction();
      throw cause;
    }
  };

  const columns = (): Promise<{column_name: string; is_nullable: string}[]> =>
    runner.query(
      `SELECT column_name, is_nullable FROM information_schema.columns
       WHERE table_schema = $1 AND table_name = 'idempotency_keys' ORDER BY column_name`,
      [schema],
    );

  it('removes only expired legacy-only rows and preserves versioned data and column names', async () => {
    await runner.query(`INSERT INTO idempotency_keys (key, expires_at, created_at) VALUES
      ('expired-by-expiry', now() - interval '1 second', now()),
      ('expired-by-ttl', now() + interval '1 day', now() - interval '6 minutes')`);
    await runner.query(`INSERT INTO idempotency_keys
      (key, operation_id, request_hash_v2, response_v2, response_version, expires_at, created_at) VALUES
      ('live', 'event.create.v1', 'hash', '{"createdAt":{"type":"commonex.date","value":"2026-10-11T00:00:00.000Z"}}', 1,
        now() + interval '5 minutes', now()),
      ('expired-versioned', 'event.create.v1', 'old-hash', '{"retained":true}', 1, now() - interval '1 day', now() - interval '2 days')`);
    const before: unknown = await runner.query(`SELECT key, operation_id, request_hash_v2, response_v2,
      response_version, expires_at, created_at FROM idempotency_keys WHERE operation_id IS NOT NULL ORDER BY key`);

    await migrate();

    const after = await runner.query('SELECT * FROM idempotency_keys ORDER BY key');
    expect(after).toEqual(before);
    const metadata = await columns();
    expect(metadata.map((column) => column.column_name)).toEqual([
      'created_at',
      'expires_at',
      'key',
      'operation_id',
      'request_hash_v2',
      'response_v2',
      'response_version',
    ]);
    expect(metadata.every((column) => column.is_nullable === 'NO')).toBe(true);
    await expect(
      runner.query(`UPDATE idempotency_keys SET request_hash_v2 = NULL WHERE key = 'live'`),
    ).rejects.toMatchObject({
      driverError: {code: '23502'},
    });

    await runner.startTransaction();
    await migration.down(runner);
    await runner.commitTransaction();
    expect(await columns()).toEqual(
      expect.arrayContaining([
        {column_name: 'url', is_nullable: 'YES'},
        {column_name: 'request_hash', is_nullable: 'YES'},
        {column_name: 'response', is_nullable: 'YES'},
        {column_name: 'status_code', is_nullable: 'YES'},
        {column_name: 'operation_id', is_nullable: 'YES'},
      ]),
    );
    expect(await runner.query('SELECT key FROM idempotency_keys ORDER BY key')).toEqual([
      {key: 'expired-versioned'},
      {key: 'live'},
    ]);
  });

  it.each(['legacy-only', 'operation_id', 'request_hash_v2', 'response_v2', 'response_version'])(
    'blocks active incomplete records with missing %s and rolls back cleanup',
    async (missing) => {
      await runner.query(`INSERT INTO idempotency_keys (key, expires_at, created_at)
        VALUES ('expired', now() - interval '1 day', now() - interval '2 days')`);
      await runner.query(`INSERT INTO idempotency_keys
        (key, operation_id, request_hash_v2, response_v2, response_version, expires_at, created_at)
        VALUES ('active', 'event.create.v1', 'hash', '{}', 1, now() + interval '1 day', now())`);
      const fields =
        missing === 'legacy-only' ? ['operation_id', 'request_hash_v2', 'response_v2', 'response_version'] : [missing];
      for (const field of fields) {
        await runner.query(`UPDATE idempotency_keys SET "${field}" = NULL WHERE key = 'active'`);
      }
      const before: unknown = await runner.query('SELECT * FROM idempotency_keys ORDER BY key');
      const metadata = await columns();

      await expect(migrate()).rejects.toThrow('incomplete versioned idempotency records');

      expect(await runner.query('SELECT * FROM idempotency_keys ORDER BY key')).toEqual(before);
      expect(await columns()).toEqual(metadata);
    },
  );

  it('blocks expired partially versioned records instead of deleting them', async () => {
    await runner.query(`INSERT INTO idempotency_keys (key, operation_id, expires_at, created_at)
      VALUES ('partial', 'event.create.v1', now() - interval '1 day', now() - interval '2 days')`);
    await expect(migrate()).rejects.toThrow('incomplete versioned idempotency records');
    expect(await runner.query('SELECT key FROM idempotency_keys')).toEqual([{key: 'partial'}]);
  });
});
