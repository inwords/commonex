import {DataSource, EntityManager, Repository} from 'typeorm';

import {IdempotencyKeyRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/idempotency-key.repository';

import {IdempotencyKeyEntity} from '#frameworks/relational-data-service/postgres/entities/idempotency-key.entity';
import {BaseRepository} from '#frameworks/relational-data-service/postgres/repositories/base.repository';

export class IdempotencyKeyRepository extends BaseRepository implements IdempotencyKeyRepositoryAbstract {
  readonly dataSource: DataSource;

  private readonly queryName = 'idempotency_key';

  constructor({dataSource, showQueryDetails}: {dataSource: DataSource; showQueryDetails: boolean}) {
    super(showQueryDetails);
    this.dataSource = dataSource;
  }

  readonly findByKey: IdempotencyKeyRepositoryAbstract['findByKey'] = async (key, trx) => {
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    const query = this.getRepository(ctx)
      .createQueryBuilder(this.queryName)
      .where(`${this.queryName}.key = :key`, {key});

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getOne();

    return [result ?? null, queryDetails];
  };

  readonly findAll: IdempotencyKeyRepositoryAbstract['findAll'] = async ({limit}, trx) => {
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    const query = this.getRepository(ctx).createQueryBuilder(this.queryName).limit(limit);

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getMany();

    return [result, queryDetails];
  };

  readonly insert: IdempotencyKeyRepositoryAbstract['insert'] = async (input, trx) => {
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    const query = this.getRepository(ctx).createQueryBuilder().insert().values(input);
    const queryDetails = this.getQueryDetails(query);

    await query.execute();

    return [undefined, queryDetails];
  };

  readonly delete: IdempotencyKeyRepositoryAbstract['delete'] = async (criteria, trx) => {
    // Repository.delete() refuses empty criteria; the query builder would run an unfiltered DELETE instead.
    if (Object.keys(criteria).length === 0) {
      throw new Error('Empty criteria are not allowed for the delete method');
    }

    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    const query = this.getRepository(ctx).createQueryBuilder().delete().where(criteria);

    const queryDetails = this.getQueryDetails(query);

    await query.execute();

    return [undefined, queryDetails];
  };

  private readonly getRepository = (manager?: EntityManager): Repository<IdempotencyKeyEntity> => {
    return manager != null
      ? manager.getRepository(IdempotencyKeyEntity)
      : this.dataSource.getRepository(IdempotencyKeyEntity);
  };
}
