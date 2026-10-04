import {HealthCheckResult, HealthCheckService, HealthIndicatorResult} from '@nestjs/terminus';

import {HealthCheckUseCase} from '#usecases/health/health-check.usecase';

import {HealthController} from '../health.controller';

describe('HealthController', () => {
  it('maps a successful database check to a healthy Terminus indicator', async () => {
    let databaseIndicator: (() => Promise<HealthIndicatorResult>) | undefined;
    const healthCheckService = {
      check: (indicators: (() => Promise<HealthIndicatorResult>)[]): Promise<HealthCheckResult> => {
        [databaseIndicator] = indicators;
        return Promise.resolve({} as HealthCheckResult);
      },
    } as unknown as HealthCheckService;
    const healthCheckUseCase = {
      execute: (): Promise<void> => Promise.resolve(),
    } as unknown as HealthCheckUseCase;

    await new HealthController(healthCheckService, healthCheckUseCase).check();

    await expect(databaseIndicator?.()).resolves.toEqual({database: {status: 'up'}});
  });
});
