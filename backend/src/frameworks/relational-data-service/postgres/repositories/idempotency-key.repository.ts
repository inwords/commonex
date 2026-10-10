import {DataSource} from 'typeorm';

import {IdempotencyKeyRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/idempotency-key.repository';

import {IdempotencyKeyEntity} from '#frameworks/relational-data-service/postgres/entities/idempotency-key.entity';
import {BaseRepository} from '#frameworks/relational-data-service/postgres/repositories/base.repository';

import {requireTransactionManager} from '../transaction-context';

export class IdempotencyKeyRepository extends BaseRepository implements IdempotencyKeyRepositoryAbstract {
  readonly dataSource: DataSource;

  private readonly queryName = 'idempotency_key';

  constructor({dataSource, showQueryDetails}: {dataSource: DataSource; showQueryDetails: boolean}) {
    super(showQueryDetails);
    this.dataSource = dataSource;
  }

  readonly findByKey: IdempotencyKeyRepositoryAbstract['findByKey'] = async (key, trx) => {
    const query = this.getRepository(this.dataSource, IdempotencyKeyEntity, trx)
      .createQueryBuilder(this.queryName)
      .where(`${this.queryName}.key = :key`, {key});

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getOne();

    return [result ?? null, queryDetails];
  };

  readonly findAll: IdempotencyKeyRepositoryAbstract['findAll'] = async ({limit}, trx) => {
    const query = this.getRepository(this.dataSource, IdempotencyKeyEntity, trx)
      .createQueryBuilder(this.queryName)
      .limit(limit);

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getMany();

    return [result, queryDetails];
  };

  readonly insert: IdempotencyKeyRepositoryAbstract['insert'] = async (input, trx) => {
    const query = this.getRepository(this.dataSource, IdempotencyKeyEntity, trx)
      .createQueryBuilder()
      .insert()
      .values(input);
    const queryDetails = this.getQueryDetails(query);

    await query.execute();

    return [undefined, queryDetails];
  };

  readonly tryAcquireLock: IdempotencyKeyRepositoryAbstract['tryAcquireLock'] = async (key, trx) => {
    const manager = requireTransactionManager(this.dataSource, trx);
    const queryString = 'SELECT pg_try_advisory_xact_lock(hashtextextended($1, 0)) AS "acquired"';
    const queryParameters = [key];
    const [result] = await manager.query<{acquired: boolean}[]>(queryString, queryParameters);

    return [result?.acquired === true, {queryString, queryParameters}];
  };

  readonly delete: IdempotencyKeyRepositoryAbstract['delete'] = async (criteria, trx) => {
    // Repository.delete() refuses empty criteria; the query builder would run an unfiltered DELETE instead.
    if (Object.keys(criteria).length === 0) {
      throw new Error('Empty criteria are not allowed for the delete method');
    }

    const query = this.getRepository(this.dataSource, IdempotencyKeyEntity, trx)
      .createQueryBuilder()
      .delete()
      .where(criteria);

    const queryDetails = this.getQueryDetails(query);

    await query.execute();

    return [undefined, queryDetails];
  };

  readonly deleteExpiredBatch: IdempotencyKeyRepositoryAbstract['deleteExpiredBatch'] = async (
    {expiresBefore, limit},
    trx,
  ) => {
    const repository = this.getRepository(this.dataSource, IdempotencyKeyEntity, trx);
    const candidates = repository
      .createQueryBuilder(this.queryName)
      .select(`${this.queryName}.key`)
      .where(`${this.queryName}.expires_at < :expiresBefore`, {expiresBefore})
      .orderBy(`${this.queryName}.expires_at`, 'ASC')
      .addOrderBy(`${this.queryName}.key`, 'ASC')
      .limit(limit);
    const query = repository
      .createQueryBuilder()
      .delete()
      .where(`key IN (${candidates.getQuery()})`)
      .setParameters(candidates.getParameters());
    const queryDetails = this.getQueryDetails(query);
    const result = await query.execute();

    return [result.affected ?? 0, queryDetails];
  };
}
