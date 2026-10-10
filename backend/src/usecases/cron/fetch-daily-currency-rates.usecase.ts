import {Injectable} from '@nestjs/common';

import {getCurrentDateWithoutTimeUTC} from '#packages/date-utils';
import {UseCase} from '#packages/use-case';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';

import {FetchAndSaveCurrencyRateSharedUseCase} from '#usecases/shared/fetch-and-save-currency-rate.usecase';

type Output = {acquired: false} | {acquired: true};

@Injectable()
export class FetchDailyCurrencyRatesUseCase implements UseCase<void, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly fetchAndSaveCurrencyRateSharedUseCase: FetchAndSaveCurrencyRateSharedUseCase,
  ) {}

  public async execute(): Promise<Output> {
    return this.rDataService.transaction<Output>(async (trx) => {
      if (!(await this.rDataService.tryAcquireTransactionLock('commonex:cron:currency-rates', trx))) {
        return {acquired: false};
      }

      const date = getCurrentDateWithoutTimeUTC();
      await this.fetchAndSaveCurrencyRateSharedUseCase.execute({date, trx});

      return {acquired: true};
    });
  }
}
