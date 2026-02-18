import {BaseRepository} from '#frameworks/relational-data-service/postgres/repositories/base.repository';
import {DataSource, EntityManager, Repository} from 'typeorm';
import {IQueryDetails} from '#domain/abstracts/relational-data-service/types';
import {CurrencyRateRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/currency-rate.repository';
import {CurrencyRateEntity} from '#frameworks/relational-data-service/postgres/entities/currency-rate.entity';
import {ICurrencyRate} from '#domain/entities/currency-rate.entity';

export class CurrencyRateRepository extends BaseRepository implements CurrencyRateRepositoryAbstract {
  readonly dataSource: DataSource;

  private readonly queryName = 'currency_rate';

  constructor({dataSource, showQueryDetails}: { dataSource: DataSource; showQueryDetails: boolean }) {
    super(showQueryDetails);
    this.dataSource = dataSource;
  }

  readonly findByDate: CurrencyRateRepositoryAbstract['findByDate'] = async (
    date: ICurrencyRate['date'],
    trx,
  ): Promise<[result: ICurrencyRate | null, queryDetails: IQueryDetails]> => {
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    let query = this.getRepository(ctx).createQueryBuilder(this.queryName);

    query = query.where(`${this.queryName}.date = :date`, {
      date,
    });

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getOne();

    return [result, queryDetails];
  };

  public findAll: CurrencyRateRepositoryAbstract['findAll'] = async (input, trx) => {
    const {limit} = input;
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    let query = this.getRepository(ctx).createQueryBuilder(this.queryName);

    query = query.limit(limit);

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getMany();

    return [result, queryDetails];
  };

  readonly insert: CurrencyRateRepositoryAbstract['insert'] = async (
    input: ICurrencyRate | ICurrencyRate[],
    trx,
  ): Promise<[result: undefined, queryDetails: IQueryDetails]> => {
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    const query = this.getRepository(ctx)
      .createQueryBuilder()
      .insert()
      .values(input)
      .orUpdate(['rate', 'updated_at'], ['date']);
    const queryDetails = this.getQueryDetails(query);

    await query.execute();

    return [undefined, queryDetails];
  };

  private readonly getRepository = (manager?: EntityManager): Repository<CurrencyRateEntity> => {
    return manager != null
      ? manager.getRepository(CurrencyRateEntity)
      : this.dataSource.getRepository(CurrencyRateEntity);
  };
}
