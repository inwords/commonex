import {SCHEDULE_CRON_OPTIONS} from '@nestjs/schedule/dist/schedule.constants';

import {CleanupIdempotencyKeysUseCase} from '#usecases/cron/cleanup-idempotency-keys.usecase';
import {FetchDailyCurrencyRatesUseCase} from '#usecases/cron/fetch-daily-currency-rates.usecase';

import {CurrencyRateSchedulerController} from '#api/cron/currency-rate-scheduler.controller';

describe('CurrencyRateSchedulerController', () => {
  const fetchDaily = {execute: jest.fn().mockResolvedValue(undefined)};
  const cleanup = {execute: jest.fn().mockResolvedValue(undefined)};
  const controller = new CurrencyRateSchedulerController(
    fetchDaily as unknown as FetchDailyCurrencyRatesUseCase,
    cleanup as unknown as CleanupIdempotencyKeysUseCase,
  );

  it.each(['handleCron', 'handleIdempotencyCleanup'] as const)('schedules %s daily at midnight UTC', (method) => {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- read only for its metadata, never invoked unbound
    expect(Reflect.getMetadata(SCHEDULE_CRON_OPTIONS, controller[method])).toEqual({
      cronTime: '0 0 * * *',
      timeZone: 'UTC',
    });
  });

  it('delegates to the use cases', async () => {
    await controller.handleCron();
    await controller.handleIdempotencyCleanup();

    expect(fetchDaily.execute).toHaveBeenCalledTimes(1);
    expect(cleanup.execute).toHaveBeenCalledTimes(1);
  });
});
