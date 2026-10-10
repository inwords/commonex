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
    let query = this.getRepository(this.dataSource, ExpenseEntity, trx).createQueryBuilder(this.queryName);

    query = query.where(`${this.queryName}.event_id = :eventId`, {
      eventId,
    });

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getMany();

    return [result, queryDetails];
  };

  public findAll: ExpenseRepositoryAbstract['findAll'] = async (input, trx) => {
    const {limit} = input;
    let query = this.getRepository(this.dataSource, ExpenseEntity, trx).createQueryBuilder(this.queryName);

    query = query.limit(limit);

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
