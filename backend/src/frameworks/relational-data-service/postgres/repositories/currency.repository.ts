import {BaseRepository} from '#frameworks/relational-data-service/postgres/repositories/base.repository';
import {DataSource, EntityManager, Repository} from 'typeorm';
import {IQueryDetails} from '#domain/abstracts/relational-data-service/types';
import {CurrencyRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/currency.repository';
import {ICurrency} from '#domain/entities/currency.entity';
import {CurrencyEntity} from '#frameworks/relational-data-service/postgres/entities/currency.entity';
import {createSupportedCurrencyCodesFilter} from './supported-currency-codes-filter';

export class CurrencyRepository extends BaseRepository implements CurrencyRepositoryAbstract {
  readonly dataSource: DataSource;

  private readonly queryName = 'currency';

  constructor({dataSource, showQueryDetails}: {dataSource: DataSource; showQueryDetails: boolean}) {
    super(showQueryDetails);
    this.dataSource = dataSource;
  }

  readonly findById: CurrencyRepositoryAbstract['findById'] = async (
    id: ICurrency['id'],
    trx,
  ): Promise<[result: ICurrency | null, queryDetails: IQueryDetails]> => {
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    let query = this.getRepository(ctx).createQueryBuilder(this.queryName);

    query = query.where(`${this.queryName}.id = :id`, {
      id,
    });

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getOne();

    return [result, queryDetails];
  };

  readonly findSupportedById: CurrencyRepositoryAbstract['findSupportedById'] = async (id, trx) => {
    const supportedCurrencyFilter = createSupportedCurrencyCodesFilter(this.queryName);
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    let query = this.getRepository(ctx).createQueryBuilder(this.queryName);

    query = query.where(`${this.queryName}.id = :id`, {id}).andWhere(supportedCurrencyFilter.condition, supportedCurrencyFilter.parameters);

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getOne();

    return [result, queryDetails];
  };

  public findAll: CurrencyRepositoryAbstract['findAll'] = async (input, trx) => {
    const {limit, codes, orderBy, orderDirection = 'ASC'} = input;
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    let query = this.getRepository(ctx).createQueryBuilder(this.queryName);

    if (codes != null) {
      query = query.where(codes.length > 0 ? `${this.queryName}.code IN (:...codes)` : '1 = 0', {
        codes,
      });
    }

    if (orderBy != null) {
      query = query.orderBy(`${this.queryName}.${orderBy}`, orderDirection);
    }

    if (limit != null) {
      query = query.limit(limit);
    }

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getMany();

    return [result, queryDetails];
  };

  readonly findAllSupported: CurrencyRepositoryAbstract['findAllSupported'] = async (input = {}, trx) => {
    const {limit, orderBy, orderDirection} = input;
    const supportedCurrencyFilter = createSupportedCurrencyCodesFilter(this.queryName);

    return this.findAll(
      {
        limit,
        codes: supportedCurrencyFilter.parameters['supportedCurrencyCodes'],
        orderBy,
        orderDirection,
      },
      trx,
    );
  };

  readonly insert: CurrencyRepositoryAbstract['insert'] = async (
    input: ICurrency | ICurrency[],
    trx,
  ): Promise<[result: undefined, queryDetails: IQueryDetails]> => {
    const ctx = trx?.ctx instanceof EntityManager ? trx.ctx : undefined;

    const query = this.getRepository(ctx).createQueryBuilder().insert().values(input);
    const queryDetails = this.getQueryDetails(query);

    await query.execute();

    return [undefined, queryDetails];
  };

  private readonly getRepository = (manager?: EntityManager): Repository<CurrencyEntity> => {
    return manager != null ? manager.getRepository(CurrencyEntity) : this.dataSource.getRepository(CurrencyEntity);
  };
}
