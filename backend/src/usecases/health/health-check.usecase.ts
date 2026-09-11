import {Injectable} from '@nestjs/common';
import {HealthIndicatorResult} from '@nestjs/terminus';

import {UseCase} from '#packages/use-case';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';

type Input = void;
type Output = HealthIndicatorResult;

@Injectable()
export class HealthCheckUseCase implements UseCase<Input, Output> {
  constructor(private readonly rDataService: RelationalDataServiceAbstract) {}

  public async execute(): Promise<HealthIndicatorResult> {
    await this.rDataService.healthCheck();
    return {database: {status: 'up'}};
  }
}
