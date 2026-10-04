import {Injectable} from '@nestjs/common';

import {UseCase} from '#packages/use-case';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';

@Injectable()
export class HealthCheckUseCase implements UseCase<void> {
  constructor(private readonly rDataService: RelationalDataServiceAbstract) {}

  public async execute(): Promise<void> {
    await this.rDataService.healthCheck();
  }
}
