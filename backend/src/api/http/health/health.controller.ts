import {Controller, Get} from '@nestjs/common';
import {HealthCheck, HealthCheckResult, HealthCheckService, HealthIndicatorResult} from '@nestjs/terminus';

import {HealthCheckUseCase} from '#usecases/health/health-check.usecase';

import {HealthRoutes} from './health.constants';

@Controller(HealthRoutes.root)
export class HealthController {
  constructor(
    private healthCheckService: HealthCheckService,
    private healthCheckUseCase: HealthCheckUseCase,
  ) {}

  @Get()
  @HealthCheck()
  check(): Promise<HealthCheckResult> {
    return this.healthCheckService.check([(): Promise<HealthIndicatorResult> => this.healthCheckUseCase.execute()]);
  }
}
