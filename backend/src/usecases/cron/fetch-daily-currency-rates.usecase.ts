import {Injectable} from '@nestjs/common';
import {EntityManager} from 'typeorm';

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
    return this.rDataService.transaction<Output, EntityManager>(async (ctx) => {
      const rows = await ctx.query<{acquired: boolean}[]>(
        `SELECT pg_try_advisory_xact_lock(hashtextextended($1, 0)) AS acquired`,
        ['commonex:cron:currency-rates'],
      );
      if (rows[0]?.acquired !== true) return {acquired: false};

      const date = getCurrentDateWithoutTimeUTC();
      await this.fetchAndSaveCurrencyRateSharedUseCase.execute({date, trx: {ctx}});

      return {acquired: true};
    });
  }
}
