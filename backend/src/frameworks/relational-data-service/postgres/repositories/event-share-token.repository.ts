import {DataSource, EntityManager, Repository} from 'typeorm';

import {EventShareTokenRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/event-share-token.repository';
import {IQueryDetails} from '#domain/abstracts/relational-data-service/types';
import {IEventShareToken} from '#domain/entities/event-share-token.entity';

import {EventShareTokenEntity} from '#frameworks/relational-data-service/postgres/entities/event-share-token.entity';

import {BaseRepository} from './base.repository';

export class EventShareTokenRepository extends BaseRepository implements EventShareTokenRepositoryAbstract {
  readonly dataSource: DataSource;

  private readonly queryName = 'event_share_token';

  constructor({dataSource, showQueryDetails}: {dataSource: DataSource; showQueryDetails: boolean}) {
    super(showQueryDetails);
    this.dataSource = dataSource;
  }

  readonly findByToken: EventShareTokenRepositoryAbstract['findByToken'] = async (
    token: string,
    trx,
  ): Promise<[result: IEventShareToken | null, queryDetails: IQueryDetails]> => {
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    let query = this.getRepository(ctx).createQueryBuilder(this.queryName);

    query = query.where(`${this.queryName}.token = :token`, {token});

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getOne();

    return [result, queryDetails];
  };

  readonly findOneActiveByEventId: EventShareTokenRepositoryAbstract['findOneActiveByEventId'] = async (
    eventId: string,
    trx,
  ): Promise<[result: IEventShareToken | null, queryDetails: IQueryDetails]> => {
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    let query = this.getRepository(ctx).createQueryBuilder(this.queryName);

    query = query
      .where(`${this.queryName}.event_id = :eventId`, {eventId})
      .andWhere(`${this.queryName}.expires_at >= :now`, {now: new Date()})
      .orderBy(`${this.queryName}.expires_at`, 'DESC')
      .limit(1);

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getOne();

    return [result, queryDetails];
  };

  public findAll: EventShareTokenRepositoryAbstract['findAll'] = async (input, trx) => {
    const {limit} = input;
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    let query = this.getRepository(ctx).createQueryBuilder(this.queryName);

    query = query.limit(limit);

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getMany();

    return [result, queryDetails];
  };

  readonly insert: EventShareTokenRepositoryAbstract['insert'] = async (
    input: IEventShareToken | IEventShareToken[],
    trx,
  ): Promise<[result: undefined, queryDetails: IQueryDetails]> => {
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    const query = this.getRepository(ctx).createQueryBuilder().insert().values(input);
    const queryDetails = this.getQueryDetails(query);

    await query.execute();

    return [undefined, queryDetails];
  };

  readonly deleteByToken: EventShareTokenRepositoryAbstract['deleteByToken'] = async (
    token: string,
    trx,
  ): Promise<[result: undefined, queryDetails: IQueryDetails]> => {
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    const query = this.getRepository(ctx).createQueryBuilder().delete().where('token = :token', {token});

    const queryDetails = this.getQueryDetails(query);

    await query.execute();

    return [undefined, queryDetails];
  };

  readonly deleteExpiredBatch: EventShareTokenRepositoryAbstract['deleteExpiredBatch'] = async (
    {expiresBefore, limit},
    trx,
  ) => {
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;
    const repository = this.getRepository(ctx);
    const candidates = repository
      .createQueryBuilder(this.queryName)
      .select(`${this.queryName}.token`)
      .where(`${this.queryName}.expires_at < :expiresBefore`, {expiresBefore})
      .orderBy(`${this.queryName}.expires_at`, 'ASC')
      .addOrderBy(`${this.queryName}.token`, 'ASC')
      .limit(limit);
    const query = repository
      .createQueryBuilder()
      .delete()
      .where(`token IN (${candidates.getQuery()})`)
      .setParameters(candidates.getParameters());
    const queryDetails = this.getQueryDetails(query);
    const result = await query.execute();

    return [result.affected ?? 0, queryDetails];
  };

  private readonly getRepository = (manager?: EntityManager): Repository<EventShareTokenEntity> => {
    return manager != null
      ? manager.getRepository(EventShareTokenEntity)
      : this.dataSource.getRepository(EventShareTokenEntity);
  };
}
