import {Injectable} from '@nestjs/common';
import {Cron, CronExpression} from '@nestjs/schedule';
import {FetchDailyCurrencyRatesUseCase} from '#usecases/cron/fetch-daily-currency-rates.usecase';
import {CleanupIdempotencyKeysUseCase} from '#usecases/cron/cleanup-idempotency-keys.usecase';

@Injectable()
export class CurrencyRateSchedulerController {
  constructor(
    private readonly fetchDailyCurrencyRatesUseCase: FetchDailyCurrencyRatesUseCase,
    private readonly cleanupIdempotencyKeysUseCase: CleanupIdempotencyKeysUseCase,
  ) {}

  @Cron(CronExpression.EVERY_DAY_AT_MIDNIGHT, {
    timeZone: 'UTC',
  })
  async handleCron(): Promise<void> {
    await this.fetchDailyCurrencyRatesUseCase.execute();
  }

  @Cron(CronExpression.EVERY_DAY_AT_MIDNIGHT, {
    timeZone: 'UTC',
  })
  async handleIdempotencyCleanup(): Promise<void> {
    await this.cleanupIdempotencyKeysUseCase.execute();
  }
}
