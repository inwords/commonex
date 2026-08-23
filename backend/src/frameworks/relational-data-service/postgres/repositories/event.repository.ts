import {BaseRepository} from './base.repository';
import {EventRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/event.repository';
import {DataSource, EntityManager, QueryFailedError, Repository} from 'typeorm';
import {IEvent} from '#domain/entities/event.entity';
import {IQueryDetails} from '#domain/abstracts/relational-data-service/types';
import {EventEntity} from '#frameworks/relational-data-service/postgres/entities/event.entity';
import {EventMutationConflictError} from '#domain/errors/errors';

const POSTGRES_LOCK_NOT_AVAILABLE_ERROR_CODE = '55P03';

export class EventRepository extends BaseRepository implements EventRepositoryAbstract {
  readonly dataSource: DataSource;

  private readonly queryName = 'event';

  constructor({dataSource, showQueryDetails}: {dataSource: DataSource; showQueryDetails: boolean}) {
    super(showQueryDetails);
    this.dataSource = dataSource;
  }

  readonly findById: EventRepositoryAbstract['findById'] = async (
    id: IEvent['id'],
    trx,
  ): Promise<[result: IEvent | null, queryDetails: IQueryDetails]> => {
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    let query = this.getRepository(ctx).createQueryBuilder(this.queryName);

    query = query.where(`${this.queryName}.id = :id`, {
      id,
    });

    if (trx?.lock) {
      query = query.setLock(trx.lock);

      if (trx.onLocked) {
        query = query.setOnLocked(trx.onLocked);
      }
    }

    const queryDetails = this.getQueryDetails(query);
    let result: EventEntity | null;

    try {
      result = await query.getOne();
    } catch (exception) {
      if (
        trx?.lock === 'pessimistic_write' &&
        trx.onLocked === 'nowait' &&
        exception instanceof QueryFailedError &&
        (exception.driverError as Error & {code?: string}).code === POSTGRES_LOCK_NOT_AVAILABLE_ERROR_CODE
      ) {
        throw new EventMutationConflictError();
      }

      throw exception;
    }

    return [result, queryDetails];
  };

  public findAll: EventRepositoryAbstract['findAll'] = async (input, trx) => {
    const {limit} = input;
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    let query = this.getRepository(ctx).createQueryBuilder(this.queryName);

    query = query.limit(limit);

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getMany();

    return [result, queryDetails];
  };

  readonly insert: EventRepositoryAbstract['insert'] = async (
    input: IEvent | IEvent[],
    trx,
  ): Promise<[result: undefined, queryDetails: IQueryDetails]> => {
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    const query = this.getRepository(ctx).createQueryBuilder().insert().values(input);
    const queryDetails = this.getQueryDetails(query);

    await query.execute();

    return [undefined, queryDetails];
  };

  readonly update: EventRepositoryAbstract['update'] = async (
    id: IEvent['id'],
    data: Partial<Omit<IEvent, 'id'>>,
    trx,
  ): Promise<[result: undefined, queryDetails: IQueryDetails]> => {
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    const query = this.getRepository(ctx).createQueryBuilder().update().set(data).where('id = :id', {id});

    const queryDetails = this.getQueryDetails(query);

    await query.execute();

    return [undefined, queryDetails];
  };

  private readonly getRepository = (manager?: EntityManager): Repository<EventEntity> => {
    return manager != null ? manager.getRepository(EventEntity) : this.dataSource.getRepository(EventEntity);
  };
}
