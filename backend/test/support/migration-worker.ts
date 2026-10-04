import {DataSource, QueryFailedError} from 'typeorm';

import {Init1791040619671} from '../../migrations/default/1791040619671-init';
import {runMigrations} from '../../scripts/run-migrations';
import {env} from '../../src/config';

const schema = process.argv[2];
if (!schema || !/^migration_test_[a-f0-9]+$/.test(schema)) {
  throw new Error('Invalid migration test schema');
}

const dataSource = new DataSource({
  type: 'postgres',
  host: env.POSTGRES_HOST,
  port: Number(env.POSTGRES_PORT),
  username: env.POSTGRES_USER_NAME,
  password: env.POSTGRES_PASSWORD,
  database: env.POSTGRES_DATABASE,
  schema,
  migrations: [Init1791040619671],
  extra: {
    application_name: schema,
    options: `-c search_path=${schema}`,
    lock_timeout: 3_000,
    statement_timeout: 15_000,
    query_timeout: 17_000,
  },
});

const run = async (): Promise<void> => {
  await dataSource.initialize();
  process.send?.('ready');
  await new Promise<void>((resolve) =>
    process.once('message', () => {
      resolve();
    }),
  );
  let sqlState: string | null = null;
  try {
    await runMigrations(dataSource);
  } catch (error: unknown) {
    process.exitCode = 1;
    const driverError: unknown = error instanceof QueryFailedError ? error.driverError : undefined;
    sqlState =
      typeof driverError === 'object' &&
      driverError !== null &&
      'code' in driverError &&
      typeof driverError.code === 'string'
        ? driverError.code
        : 'unknown';
  } finally {
    await dataSource.destroy();
  }
  process.send?.({sqlState}, () => {
    process.disconnect();
  });
};

void run().catch(() => {
  process.exitCode = 1;
  process.disconnect();
});
