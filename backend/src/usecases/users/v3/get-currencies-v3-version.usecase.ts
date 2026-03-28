import {Injectable} from '@nestjs/common';
import {CurrencyRateNotFoundError} from '#domain/errors';
import {getCurrentDateWithoutTimeUTC} from '#packages/date-utils';
import {error, Result, success} from '#packages/result';
import {UseCase} from '#packages/use-case';
import {SupportedCurrencyServiceAbstract} from '#domain/abstracts/supported-currency-service/supported-currency-service';
import {buildCurrenciesV3Version, CurrenciesV3Version} from './currencies-v3-cache';

type Output = Result<CurrenciesV3Version, CurrencyRateNotFoundError>;

@Injectable()
export class GetCurrenciesV3VersionUseCase implements UseCase<void, Output> {
  constructor(private readonly supportedCurrencyService: SupportedCurrencyServiceAbstract) {}

  public async execute(): Promise<Output> {
    const currentDate = getCurrentDateWithoutTimeUTC();
    const version = await this.supportedCurrencyService.findVersionByDate(currentDate);

    if (!version) {
      return error(new CurrencyRateNotFoundError());
    }

    return success(buildCurrenciesV3Version(version));
  }
}
