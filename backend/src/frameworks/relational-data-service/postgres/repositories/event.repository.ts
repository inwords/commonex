import {DataSource} from 'typeorm';

import {EventRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/event.repository';
import {IQueryDetails} from '#domain/abstracts/relational-data-service/types';
import {IEvent} from '#domain/entities/event.entity';

import {EventEntity} from '#frameworks/relational-data-service/postgres/entities/event.entity';

import {BaseRepository} from './base.repository';

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
    let query = this.getRepository(this.dataSource, EventEntity, trx).createQueryBuilder(this.queryName);

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
    const result = await query.getOne();

    return [result, queryDetails];
  };

  public findAll: EventRepositoryAbstract['findAll'] = async (input, trx) => {
    const {limit} = input;
    let query = this.getRepository(this.dataSource, EventEntity, trx).createQueryBuilder(this.queryName);

    query = query.limit(limit);

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getMany();

    return [result, queryDetails];
  };

  readonly insert: EventRepositoryAbstract['insert'] = async (
    input: IEvent | IEvent[],
    trx,
  ): Promise<[result: undefined, queryDetails: IQueryDetails]> => {
    const query = this.getRepository(this.dataSource, EventEntity, trx).createQueryBuilder().insert().values(input);
    const queryDetails = this.getQueryDetails(query);

    await query.execute();

    return [undefined, queryDetails];
  };

  readonly update: EventRepositoryAbstract['update'] = async (
    id: IEvent['id'],
    data: Partial<Omit<IEvent, 'id'>>,
    trx,
  ): Promise<[result: undefined, queryDetails: IQueryDetails]> => {
    const query = this.getRepository(this.dataSource, EventEntity, trx)
      .createQueryBuilder()
      .update()
      .set(data)
      .where('id = :id', {id});

    const queryDetails = this.getQueryDetails(query);

    await query.execute();

    return [undefined, queryDetails];
  };
}
