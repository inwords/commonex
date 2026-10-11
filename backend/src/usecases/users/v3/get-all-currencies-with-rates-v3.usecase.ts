import {Injectable} from '@nestjs/common';

import {getCurrentDateWithoutTimeUTC} from '#packages/date-utils';
import {Result, error, success} from '#packages/result';
import {UseCase} from '#packages/use-case';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {SupportedCurrencyServiceAbstract} from '#domain/abstracts/supported-currency-service/supported-currency-service';
import {CurrencyCode} from '#domain/entities/currency.entity';
import {CurrencyRateNotFoundError} from '#domain/errors';

import {CurrenciesV3Version, buildCurrenciesV3VersionFromResponse} from './currencies-v3-cache';

type SupportedExchangeRateMap = Partial<Record<CurrencyCode, number>>;

type Output = Result<
  {
    response: {
      currencies: {
        id: string;
        code: CurrencyCode;
        updatedAt: Date;
      }[];
      exchangeRate: SupportedExchangeRateMap;
    };
    version: CurrenciesV3Version;
  },
  CurrencyRateNotFoundError
>;

@Injectable()
export class GetAllCurrenciesWithRatesUseCaseV3 implements UseCase<void, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly supportedCurrencyService: SupportedCurrencyServiceAbstract,
  ) {}

  public async execute(): Promise<Output> {
    const currentDate = getCurrentDateWithoutTimeUTC();
    const [[currencies], [currencyRate]] = await Promise.all([
      this.rDataService.currency.findAll({
        orderBy: 'code',
        orderDirection: 'ASC',
      }),
      this.rDataService.currencyRate.findByDate(currentDate),
    ]);

    if (!currencyRate) {
      return error(new CurrencyRateNotFoundError());
    }

    const exchangeRate: SupportedExchangeRateMap = this.supportedCurrencyService.filterRates(currencyRate.rate);
    const response = {
      currencies: this.supportedCurrencyService.filterCurrencies(currencies).map((currency) => ({
        id: currency.id,
        code: currency.code,
        updatedAt: currency.updatedAt,
      })),
      exchangeRate,
    };

    return success({
      response,
      version: buildCurrenciesV3VersionFromResponse({
        rateUpdatedAt: currencyRate.updatedAt,
        currencies: response.currencies,
      }),
    });
  }
}
