import {DataSource, MigrationExecutor} from 'typeorm';

export const runMigrations = async (dataSource: DataSource): Promise<void> => {
  const queryRunner = dataSource.createQueryRunner();
  const schema = 'schema' in dataSource.options ? dataSource.options.schema : undefined;
  const lockName = `commonex:migrations:${schema ?? 'public'}`;
  let locked = false;
  try {
    await queryRunner.connect();
    // The session lock covers ledger discovery and all per-migration transactions.
    await queryRunner.query('SELECT pg_advisory_lock(hashtextextended($1, 0))', [lockName]);
    locked = true;
    const executor = new MigrationExecutor(dataSource, queryRunner);
    executor.transaction = 'each';
    await executor.executePendingMigrations();
  } finally {
    try {
      if (locked) {
        await queryRunner.query('SELECT pg_advisory_unlock(hashtextextended($1, 0))', [lockName]);
      }
    } finally {
      await queryRunner.release();
    }
  }
};
