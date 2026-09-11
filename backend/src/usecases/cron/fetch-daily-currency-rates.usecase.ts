import {Injectable} from '@nestjs/common';

import {getCurrentDateWithoutTimeUTC} from '#packages/date-utils';
import {UseCase} from '#packages/use-case';

import {FetchAndSaveCurrencyRateSharedUseCase} from '#usecases/shared/fetch-and-save-currency-rate.usecase';

@Injectable()
export class FetchDailyCurrencyRatesUseCase implements UseCase<void> {
  constructor(private readonly fetchAndSaveCurrencyRateSharedUseCase: FetchAndSaveCurrencyRateSharedUseCase) {}

  public async execute(): Promise<void> {
    const date = getCurrentDateWithoutTimeUTC();

    await this.fetchAndSaveCurrencyRateSharedUseCase.execute({date});
  }
}
