import {SupportedCurrenciesVersion, SupportedCurrencyServiceAbstract} from '#domain/abstracts/supported-currency-service/supported-currency-service';
import {ITransaction, ITransactionWithLock} from '#domain/abstracts/relational-data-service/types';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {ICurrency} from '#domain/entities/currency.entity';
import {ICurrencyRate} from '#domain/entities/currency-rate.entity';
import {SUPPORTED_CURRENCY_CODES} from '../../constants';

const filterSupportedRates = (rate: Record<string, number>): Record<string, number> => {
  return Object.fromEntries(Object.entries(rate).filter(([currencyCode]) => SUPPORTED_CURRENCY_CODES.includes(currencyCode as ICurrency['code'])));
};

export class SupportedCurrencyService implements SupportedCurrencyServiceAbstract {
  constructor(private readonly rDataService: RelationalDataServiceAbstract) {}

  readonly findById = async (currencyId: ICurrency['id'], trx?: ITransactionWithLock): Promise<ICurrency | null> => {
    const [currency] = await this.rDataService.currency.findSupportedById(currencyId, trx);

    return currency;
  };

  readonly findAll = async (
    input?: {
      limit?: number;
      orderBy?: 'id' | 'code' | 'createdAt' | 'updatedAt';
      orderDirection?: 'ASC' | 'DESC';
    },
    trx?: ITransaction,
  ): Promise<ICurrency[]> => {
    const [currencies] = await this.rDataService.currency.findAllSupported(input, trx);

    return currencies;
  };

  readonly findRateByDate = async (date: ICurrencyRate['date'], trx?: ITransactionWithLock): Promise<ICurrencyRate | null> => {
    const [currencyRate] = await this.rDataService.currencyRate.findByDate(date, trx);

    if (!currencyRate) {
      return null;
    }

    return {
      ...currencyRate,
      rate: filterSupportedRates(currencyRate.rate),
    };
  };

  readonly findVersionByDate = async (date: ICurrencyRate['date'], trx?: ITransactionWithLock): Promise<SupportedCurrenciesVersion | null> => {
    const [version] = await this.rDataService.currencyRate.findSupportedCurrenciesWithRatesVersionByDate(date, trx);

    return version;
  };
}
