import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';

import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {TestCase} from '#test-support/relational-state';

import {HealthCheckUseCase} from '../health-check.usecase';

type HealthCheckTestCase = TestCase<HealthCheckUseCase>;

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

  const testCases: HealthCheckTestCase[] = [
    {
      name: 'returns status up when the database is reachable',
      initRelationalState: {},
      input: undefined,
      output: {database: {status: 'up'}},
    },
  ];

  testCases.forEach((testCase) => {
    it(testCase.name, async () => {
      const result = await useCase.execute();

      expect(result).toEqual(testCase.output);
    });
  });
});
