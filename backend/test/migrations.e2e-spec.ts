import {ChildProcess, fork} from 'child_process';
import {randomUUID} from 'crypto';
import {join} from 'path';

import {DataSource, MigrationExecutor, QueryRunner} from 'typeorm';

import {env} from '../src/config';

interface Worker {
  child: ChildProcess;
  ready: Promise<void>;
  result: Promise<{exitCode: number | null; sqlState: string | null}>;
}

describe('Migration startup', () => {
  let schema: string;
  let dataSource: DataSource;
  let blocker: QueryRunner;
  let workers: Worker[];

  beforeEach(async () => {
    schema = `migration_test_${randomUUID().replaceAll('-', '')}`;
    workers = [];
    dataSource = new DataSource({
      type: 'postgres',
      host: env.POSTGRES_HOST,
      port: Number(env.POSTGRES_PORT),
      username: env.POSTGRES_USER_NAME,
      password: env.POSTGRES_PASSWORD,
      database: env.POSTGRES_DATABASE,
      schema,
    });
    await dataSource.initialize();
    await dataSource.query(`CREATE SCHEMA "${schema}"`);
    await dataSource.query(`CREATE TABLE "${schema}".idempotency_keys (
      url varchar NOT NULL,
      request_hash varchar NOT NULL,
      response jsonb NOT NULL,
      status_code integer NOT NULL
    )`);
    await new MigrationExecutor(dataSource).executePendingMigrations();
    blocker = dataSource.createQueryRunner();
    await blocker.connect();
  });

  afterEach(async () => {
    for (const worker of workers) {
      if (worker.child.exitCode === null) {
        worker.child.kill();
      }
    }
    await Promise.allSettled(workers.map((worker) => worker.result));
    if (blocker.isTransactionActive) {
      await blocker.rollbackTransaction();
    }
    await blocker.release();
    await dataSource.query(`DROP SCHEMA "${schema}" CASCADE`);
    await dataSource.destroy();
  });

  const startWorker = (): Worker => {
    const child = fork(join(__dirname, 'support/migration-worker.ts'), [schema], {
      execArgv: ['-r', require.resolve('ts-node/register/transpile-only')],
      env: {
        ...process.env,
        TS_NODE_PROJECT: join(__dirname, '../tsconfig.json'),
        TS_NODE_COMPILER_OPTIONS: JSON.stringify({moduleResolution: 'node', ignoreDeprecations: '6.0'}),
      },
      stdio: ['ignore', 'ignore', 'ignore', 'ipc'],
    });
    let sqlState: string | null = null;
    const ready = new Promise<void>((resolve, reject) => {
      child.on('message', (message: unknown) => {
        if (message === 'ready') {
          resolve();
        }
      });
      child.once('error', reject);
      child.once('exit', () => {
        reject(new Error('Migration worker exited before becoming ready'));
      });
    });
    const result = new Promise<{exitCode: number | null; sqlState: string | null}>((resolve, reject) => {
      child.on('message', (message: unknown) => {
        if (typeof message === 'object' && message !== null && 'sqlState' in message) {
          sqlState = typeof message.sqlState === 'string' ? message.sqlState : null;
        }
      });
      child.once('error', reject);
      child.once('exit', (exitCode) => {
        resolve({exitCode, sqlState});
      });
    });
    const worker = {child, ready, result};
    workers.push(worker);
    return worker;
  };

  const columnNames = async (): Promise<string[]> => {
    const rows = await dataSource.query<{column_name: string}[]>(
      'SELECT column_name FROM information_schema.columns WHERE table_schema = $1 AND table_name = $2',
      [schema, 'idempotency_keys'],
    );
    return rows.map((row) => row.column_name);
  };

  it('serializes two startup processes across discovery and migration execution', async () => {
    const first = startWorker();
    const second = startWorker();
    await Promise.all([first.ready, second.ready]);
    await blocker.startTransaction();
    await blocker.query(`LOCK TABLE "${schema}".idempotency_keys IN ACCESS EXCLUSIVE MODE`);
    first.child.send('start');
    second.child.send('start');
    const deadline = Date.now() + 2_000;
    let blocked = 0;
    while (Date.now() < deadline && blocked !== 2) {
      const [row] = await dataSource.query<{count: number}[]>(
        `SELECT COUNT(*)::integer AS count FROM pg_stat_activity
         WHERE application_name = $1 AND state = 'active' AND wait_event_type = 'Lock'`,
        [schema],
      );
      blocked = row?.count ?? 0;
      if (blocked !== 2) {
        await new Promise<void>((resolve) => setTimeout(resolve, 10));
      }
    }
    expect(blocked).toBe(2);
    await blocker.commitTransaction();
    expect(await Promise.all([first.result, second.result])).toEqual([
      {exitCode: 0, sqlState: null},
      {exitCode: 0, sqlState: null},
    ]);
    const records = await dataSource.query<{count: number}[]>(
      `SELECT COUNT(*)::integer AS count FROM "${schema}".migrations WHERE name = $1`,
      ['Init1791040619671'],
    );
    expect(records).toEqual([{count: 1}]);
    expect(await columnNames()).toEqual(
      expect.arrayContaining(['operation_id', 'request_hash_v2', 'response_v2', 'response_version']),
    );
  });

  it('rolls back a failed migration and releases its session for a later startup', async () => {
    await dataSource.query(`ALTER TABLE "${schema}".idempotency_keys DROP COLUMN response`);
    const failed = startWorker();
    await failed.ready;
    failed.child.send('start');
    expect(await failed.result).toEqual({exitCode: 1, sqlState: '42703'});
    expect(await columnNames()).not.toContain('operation_id');
    expect(await dataSource.query(`SELECT name FROM "${schema}".migrations`)).toEqual([]);
    await dataSource.query(`ALTER TABLE "${schema}".idempotency_keys ADD COLUMN response jsonb NOT NULL`);
    const retry = startWorker();
    await retry.ready;
    retry.child.send('start');
    expect(await retry.result).toEqual({exitCode: 0, sqlState: null});
  });
  it('preserves the configured lock timeout and allows startup after contention clears', async () => {
    const lockName = `commonex:migrations:${schema}`;
    await blocker.query('SELECT pg_advisory_lock(hashtextextended($1, 0))', [lockName]);
    try {
      const blocked = startWorker();
      await blocked.ready;
      blocked.child.send('start');
      expect(await blocked.result).toEqual({exitCode: 1, sqlState: '55P03'});
      expect(await columnNames()).not.toContain('operation_id');
      expect(await dataSource.query(`SELECT name FROM "${schema}".migrations`)).toEqual([]);
    } finally {
      await blocker.query('SELECT pg_advisory_unlock(hashtextextended($1, 0))', [lockName]);
    }
    const retry = startWorker();
    await retry.ready;
    retry.child.send('start');
    expect(await retry.result).toEqual({exitCode: 0, sqlState: null});
  });
});
