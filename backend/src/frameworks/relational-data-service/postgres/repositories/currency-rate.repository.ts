import {DataSource} from 'typeorm';

import {CurrencyRateRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/currency-rate.repository';
import {IQueryDetails} from '#domain/abstracts/relational-data-service/types';
import {ICurrencyRate} from '#domain/entities/currency-rate.entity';

import {CurrencyRateEntity} from '#frameworks/relational-data-service/postgres/entities/currency-rate.entity';
import {CurrencyEntity} from '#frameworks/relational-data-service/postgres/entities/currency.entity';
import {BaseRepository} from '#frameworks/relational-data-service/postgres/repositories/base.repository';

export class CurrencyRateRepository extends BaseRepository implements CurrencyRateRepositoryAbstract {
  readonly dataSource: DataSource;

  private readonly queryName = 'currency_rate';

  constructor({dataSource, showQueryDetails}: {dataSource: DataSource; showQueryDetails: boolean}) {
    super(showQueryDetails);
    this.dataSource = dataSource;
  }

  readonly findByDate: CurrencyRateRepositoryAbstract['findByDate'] = async (
    date: ICurrencyRate['date'],
    trx,
  ): Promise<[result: ICurrencyRate | null, queryDetails: IQueryDetails]> => {
    let query = this.getRepository(this.dataSource, CurrencyRateEntity, trx).createQueryBuilder(this.queryName);

    query = query.where(`${this.queryName}.date = :date`, {
      date,
    });

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getOne();

    return [result, queryDetails];
  };

  readonly findCurrenciesWithRatesVersionByDate: CurrencyRateRepositoryAbstract['findCurrenciesWithRatesVersionByDate'] =
    async (date, codes, trx) => {
      let query = this.getRepository(this.dataSource, CurrencyRateEntity, trx)
        .createQueryBuilder(this.queryName)
        .select(`${this.queryName}.updatedAt`, 'rateUpdatedAt')
        .addSelect((subQuery) => {
          let currencyQuery = subQuery
            .select(`COALESCE(json_agg(currency.updatedAt ORDER BY currency.code ASC), '[]'::json)`)
            .from(CurrencyEntity, 'currency');

          currencyQuery = currencyQuery.where(codes.length > 0 ? 'currency.code IN (:...codes)' : '1 = 0', {
            codes,
          });

          return currencyQuery;
        }, 'currenciesUpdatedAt');

      query = query.where(`${this.queryName}.date = :date`, {date});

      const queryDetails = this.getQueryDetails(query);
      const result = await query.getRawOne<{
        rateUpdatedAt: Date;
        currenciesUpdatedAt: string[];
      }>();

      if (result == null) {
        return [null, queryDetails];
      }

      return [
        {
          rateUpdatedAt: result.rateUpdatedAt,
          currenciesUpdatedAt: result.currenciesUpdatedAt.map((updatedAt) => new Date(updatedAt)),
        },
        queryDetails,
      ];
    };

  public findAll: CurrencyRateRepositoryAbstract['findAll'] = async (input, trx) => {
    const {limit} = input;
    let query = this.getRepository(this.dataSource, CurrencyRateEntity, trx).createQueryBuilder(this.queryName);

    query = query.limit(limit);

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getMany();

    return [result, queryDetails];
  };

  readonly insert: CurrencyRateRepositoryAbstract['insert'] = async (
    input: ICurrencyRate | ICurrencyRate[],
    trx,
  ): Promise<[result: undefined, queryDetails: IQueryDetails]> => {
    const query = this.getRepository(this.dataSource, CurrencyRateEntity, trx)
      .createQueryBuilder()
      .insert()
      .values(input)
      .orUpdate(['rate', 'updated_at'], ['date']);
    const queryDetails = this.getQueryDetails(query);

    await query.execute();

    return [undefined, queryDetails];
  };
}
