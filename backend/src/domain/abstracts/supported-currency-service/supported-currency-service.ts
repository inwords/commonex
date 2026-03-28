import {ITransaction, ITransactionWithLock} from '#domain/abstracts/relational-data-service/types';
import {ICurrency} from '#domain/entities/currency.entity';
import {ICurrencyRate} from '#domain/entities/currency-rate.entity';

export interface SupportedCurrenciesVersion {
  rateUpdatedAt: ICurrencyRate['updatedAt'];
  currenciesUpdatedAt: Array<ICurrency['updatedAt']>;
}

export abstract class SupportedCurrencyServiceAbstract {
  abstract findById: (currencyId: ICurrency['id'], trx?: ITransactionWithLock) => Promise<ICurrency | null>;
  abstract findAll: (
    input?: {
      limit?: number;
      orderBy?: 'id' | 'code' | 'createdAt' | 'updatedAt';
      orderDirection?: 'ASC' | 'DESC';
    },
    trx?: ITransaction,
  ) => Promise<ICurrency[]>;
  abstract findRateByDate: (date: ICurrencyRate['date'], trx?: ITransactionWithLock) => Promise<ICurrencyRate | null>;
  abstract findVersionByDate: (date: ICurrencyRate['date'], trx?: ITransactionWithLock) => Promise<SupportedCurrenciesVersion | null>;
}
