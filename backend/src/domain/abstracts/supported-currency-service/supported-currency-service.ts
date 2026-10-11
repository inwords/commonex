import {ICurrencyRate} from '#domain/entities/currency-rate.entity';
import {CurrencyCode, ICurrency} from '#domain/entities/currency.entity';

export abstract class SupportedCurrencyServiceAbstract {
  abstract readonly supportedCodes: readonly CurrencyCode[];
  abstract isSupported: (currency: ICurrency | null) => currency is ICurrency;
  abstract filterCurrencies: (currencies: readonly ICurrency[]) => ICurrency[];
  abstract filterRates: (rates: ICurrencyRate['rate']) => ICurrencyRate['rate'];
}
