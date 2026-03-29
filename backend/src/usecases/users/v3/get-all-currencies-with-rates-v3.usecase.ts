import {UseCase} from '#packages/use-case';
import {Injectable} from '@nestjs/common';
import {SupportedCurrencyServiceAbstract} from '#domain/abstracts/supported-currency-service/supported-currency-service';
import {CurrencyCode} from '#domain/entities/currency.entity';
import {error, Result, success} from '#packages/result';
import {getCurrentDateWithoutTimeUTC} from '#packages/date-utils';
import {CurrencyRateNotFoundError} from '#domain/errors';
import {buildCurrenciesV3VersionFromResponse, CurrenciesV3Version} from './currencies-v3-cache';

type SupportedExchangeRateMap = Partial<Record<CurrencyCode, number>>;

type Output = Result<
  {
    response: {
      currencies: Array<{
        id: string;
        code: CurrencyCode;
        updatedAt: Date;
      }>;
      exchangeRate: SupportedExchangeRateMap;
    };
    version: CurrenciesV3Version;
  },
  CurrencyRateNotFoundError
>;

@Injectable()
export class GetAllCurrenciesWithRatesUseCaseV3 implements UseCase<void, Output> {
  constructor(private readonly supportedCurrencyService: SupportedCurrencyServiceAbstract) {}

  public async execute(): Promise<Output> {
    const currentDate = getCurrentDateWithoutTimeUTC();
    const [currencies, currencyRate] = await Promise.all([
      this.supportedCurrencyService.findAll({
        orderBy: 'code',
        orderDirection: 'ASC',
      }),
      this.supportedCurrencyService.findRateByDate(currentDate),
    ]);

    if (!currencyRate) {
      return error(new CurrencyRateNotFoundError());
    }

    const exchangeRate: SupportedExchangeRateMap = currencyRate.rate;
    const response = {
      currencies: currencies.map((currency) => ({
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
