import {Injectable} from '@nestjs/common';

import {getCurrentDateWithoutTimeUTC} from '#packages/date-utils';
import {Result, error, success} from '#packages/result';
import {UseCase} from '#packages/use-case';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {SupportedCurrencyServiceAbstract} from '#domain/abstracts/supported-currency-service/supported-currency-service';
import {CurrencyRateNotFoundError} from '#domain/errors';

import {CurrenciesV3Version, buildCurrenciesV3Version} from './currencies-v3-cache';

type Output = Result<CurrenciesV3Version, CurrencyRateNotFoundError>;

@Injectable()
export class GetCurrenciesV3VersionUseCase implements UseCase<void, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly supportedCurrencyService: SupportedCurrencyServiceAbstract,
  ) {}

  public async execute(): Promise<Output> {
    const currentDate = getCurrentDateWithoutTimeUTC();
    const [version] = await this.rDataService.currencyRate.findCurrenciesWithRatesVersionByDate(currentDate, [
      ...this.supportedCurrencyService.supportedCodes,
    ]);

    if (!version) {
      return error(new CurrencyRateNotFoundError());
    }

    return success(buildCurrenciesV3Version(version));
  }
}
