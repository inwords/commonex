import {Injectable, Logger} from '@nestjs/common';
import {Cron, CronExpression} from '@nestjs/schedule';

import {CleanupExpiredDataUseCase} from '#usecases/cron/cleanup-expired-data.usecase';
import {FetchDailyCurrencyRatesUseCase} from '#usecases/cron/fetch-daily-currency-rates.usecase';

@Injectable()
export class CurrencyRateSchedulerController {
  private readonly logger = new Logger(CurrencyRateSchedulerController.name);

  constructor(
    private readonly fetchDailyCurrencyRatesUseCase: FetchDailyCurrencyRatesUseCase,
    private readonly cleanupExpiredDataUseCase: CleanupExpiredDataUseCase,
  ) {}

  @Cron(CronExpression.EVERY_DAY_AT_MIDNIGHT, {
    timeZone: 'UTC',
  })
  async handleCron(): Promise<void> {
    const startedAt = Date.now();
    const outcome = await this.fetchDailyCurrencyRatesUseCase.execute();

    this.logger.log({
      job: 'currency-rates',
      status: outcome.acquired ? 'completed' : 'skipped-lock-held',
      durationMs: Date.now() - startedAt,
    });
  }

  @Cron(CronExpression.EVERY_DAY_AT_MIDNIGHT, {
    timeZone: 'UTC',
  })
  async handleRetentionCleanup(): Promise<void> {
    const startedAt = Date.now();
    const outcome = await this.cleanupExpiredDataUseCase.execute();

    this.logger.log({
      job: 'data-retention',
      status: outcome.acquired ? 'completed' : 'skipped-lock-held',
      durationMs: Date.now() - startedAt,
      ...(outcome.acquired ? outcome.result : {}),
    });
  }
}
