import {SupportedCurrencyServiceAbstract} from '#domain/abstracts/supported-currency-service/supported-currency-service';
import {ICurrencyRate} from '#domain/entities/currency-rate.entity';
import {ICurrency} from '#domain/entities/currency.entity';

import {SUPPORTED_CURRENCY_CODES} from '../../constants';

export class SupportedCurrencyService implements SupportedCurrencyServiceAbstract {
  readonly supportedCodes = Object.freeze([...SUPPORTED_CURRENCY_CODES]);

  readonly isSupported = (currency: ICurrency | null): currency is ICurrency => {
    return currency != null && this.supportedCodes.includes(currency.code);
  };

  readonly filterCurrencies = (currencies: readonly ICurrency[]): ICurrency[] => {
    return currencies.filter(this.isSupported);
  };

  readonly filterRates = (rates: ICurrencyRate['rate']): ICurrencyRate['rate'] => {
    return Object.fromEntries(
      Object.entries(rates).filter(([code]) => this.supportedCodes.includes(code as ICurrency['code'])),
    );
  };
}
