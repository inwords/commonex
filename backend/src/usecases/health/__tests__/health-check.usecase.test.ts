import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';

import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {HealthCheckUseCase} from '../health-check.usecase';

describe('HealthCheckUseCase', () => {
  let relationalDataService: RelationalDataServiceAbstract;
  let useCase: HealthCheckUseCase;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    useCase = new HealthCheckUseCase(relationalDataService);

    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  it('completes when the database is reachable', async () => {
    await expect(useCase.execute()).resolves.toBeUndefined();
  });
});
