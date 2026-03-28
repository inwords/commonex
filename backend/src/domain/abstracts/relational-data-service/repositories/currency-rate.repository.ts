import {IQueryDetails, ITransaction, ITransactionWithLock} from '#domain/abstracts/relational-data-service/types';
import {ICurrency} from '#domain/entities/currency.entity';
import {ICurrencyRate} from '#domain/entities/currency-rate.entity';

export abstract class CurrencyRateRepositoryAbstract {
  abstract findByDate: (date: string, trx?: ITransactionWithLock) => Promise<[result: ICurrencyRate | null, queryDetails: IQueryDetails]>;

  abstract findSupportedCurrenciesWithRatesVersionByDate: (
    date: string,
    trx?: ITransactionWithLock,
  ) => Promise<
    [
      result: {
        rateUpdatedAt: ICurrencyRate['updatedAt'];
        currenciesUpdatedAt: Array<ICurrency['updatedAt']>;
      } | null,
      queryDetails: IQueryDetails,
    ]
  >;

  abstract findAll: (input: {limit: number}, trx?: ITransaction) => Promise<[result: ICurrencyRate[], queryDetails: IQueryDetails]>;

  abstract insert: (currencyRate: ICurrencyRate | ICurrencyRate[], trx?: ITransaction) => Promise<[result: undefined, queryDetails: IQueryDetails]>;
}
