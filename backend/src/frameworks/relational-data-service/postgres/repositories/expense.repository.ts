import {DataSource} from 'typeorm';

import {ExpenseRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/expense.repository';
import {IQueryDetails} from '#domain/abstracts/relational-data-service/types';
import {IExpense} from '#domain/entities/expense.entity';

import {ExpenseEntity} from '#frameworks/relational-data-service/postgres/entities/expense.entity';
import {BaseRepository} from '#frameworks/relational-data-service/postgres/repositories/base.repository';

export class ExpenseRepository extends BaseRepository implements ExpenseRepositoryAbstract {
  readonly dataSource: DataSource;

  private readonly queryName = 'expense';

  constructor({dataSource, showQueryDetails}: {dataSource: DataSource; showQueryDetails: boolean}) {
    super(showQueryDetails);
    this.dataSource = dataSource;
  }

  readonly findByEventId: ExpenseRepositoryAbstract['findByEventId'] = async (
    eventId: IExpense['eventId'],
    trx,
  ): Promise<[result: IExpense[], queryDetails: IQueryDetails]> => {
    const query = this.getRepository(this.dataSource, ExpenseEntity, trx)
      .createQueryBuilder(this.queryName)
      .distinctOn([`${this.queryName}.id`])
      .where(`${this.queryName}.event_id = :eventId`, {eventId})
      .orderBy(`${this.queryName}.id`, 'ASC')
      .addOrderBy(`${this.queryName}.version`, 'DESC');

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getMany();

    return [result, queryDetails];
  };

  readonly findLatestById: ExpenseRepositoryAbstract['findLatestById'] = async (eventId, id, trx) => {
    const query = this.getRepository(this.dataSource, ExpenseEntity, trx)
      .createQueryBuilder(this.queryName)
      .where(`${this.queryName}.event_id = :eventId`, {eventId})
      .andWhere(`${this.queryName}.id = :id`, {id})
      .orderBy(`${this.queryName}.version`, 'DESC')
      .limit(1);
    if (trx?.lock) {
      query.setLock(trx.lock);
    }
    const queryDetails = this.getQueryDetails(query);
    const result = await query.getOne();
    return [result, queryDetails];
  };

  readonly findHistoryById: ExpenseRepositoryAbstract['findHistoryById'] = async (eventId, id, trx) => {
    const query = this.getRepository(this.dataSource, ExpenseEntity, trx)
      .createQueryBuilder(this.queryName)
      .where(`${this.queryName}.event_id = :eventId`, {eventId})
      .andWhere(`${this.queryName}.id = :id`, {id})
      .orderBy(`${this.queryName}.version`, 'ASC');
    const queryDetails = this.getQueryDetails(query);
    const result = await query.getMany();
    return [result, queryDetails];
  };

  public findAll: ExpenseRepositoryAbstract['findAll'] = async (input, trx) => {
    const {limit} = input;
    const query = this.getRepository(this.dataSource, ExpenseEntity, trx)
      .createQueryBuilder(this.queryName)
      .distinctOn([`${this.queryName}.id`])
      .orderBy(`${this.queryName}.id`, 'ASC')
      .addOrderBy(`${this.queryName}.version`, 'DESC')
      .limit(limit);

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getMany();

    return [result, queryDetails];
  };

  readonly insert: ExpenseRepositoryAbstract['insert'] = async (
    input: IExpense | IExpense[],
    trx,
  ): Promise<[result: undefined, queryDetails: IQueryDetails]> => {
    const query = this.getRepository(this.dataSource, ExpenseEntity, trx).createQueryBuilder().insert().values(input);
    const queryDetails = this.getQueryDetails(query);

    await query.execute();

    return [undefined, queryDetails];
  };
}
