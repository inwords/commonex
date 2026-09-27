import {SCHEDULE_CRON_OPTIONS} from '@nestjs/schedule/dist/schedule.constants';

import {CleanupExpiredDataUseCase} from '#usecases/cron/cleanup-expired-data.usecase';
import {FetchDailyCurrencyRatesUseCase} from '#usecases/cron/fetch-daily-currency-rates.usecase';

import {CurrencyRateSchedulerController} from '#api/cron/currency-rate-scheduler.controller';

describe('CurrencyRateSchedulerController', () => {
  const fetchDaily = {execute: jest.fn().mockResolvedValue({acquired: true})};
  const cleanupExpiredData = {
    execute: jest.fn().mockResolvedValue({
      acquired: true,
      result: {
        idempotencyKeys: {deletedCount: 3, batchCount: 1},
        eventShareTokens: {deletedCount: 4, batchCount: 1},
      },
    }),
  };
  const controller = new CurrencyRateSchedulerController(
    fetchDaily as unknown as FetchDailyCurrencyRatesUseCase,
    cleanupExpiredData as unknown as CleanupExpiredDataUseCase,
  );

  it.each(['handleCron', 'handleRetentionCleanup'] as const)('schedules %s daily at midnight UTC', (method) => {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- read only for its metadata, never invoked unbound
    expect(Reflect.getMetadata(SCHEDULE_CRON_OPTIONS, controller[method])).toEqual({
      cronTime: '0 0 * * *',
      timeZone: 'UTC',
    });
  });

  it('runs currency refresh and retention cleanup', async () => {
    await controller.handleCron();
    await controller.handleRetentionCleanup();

    expect(fetchDaily.execute).toHaveBeenCalledTimes(1);
    expect(cleanupExpiredData.execute).toHaveBeenCalledTimes(1);
  });
});
