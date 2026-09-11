import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';

import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {TestCase, prepareInitRelationalState} from '../../__tests__/test-helpers';
import {GetCurrencyRateUseCase} from '../get-currency-rate.usecase';

type GetCurrencyRateTestCase = TestCase<GetCurrencyRateUseCase>;

describe('GetCurrencyRateUseCase', () => {
  let relationalDataService: RelationalDataServiceAbstract;
  let useCase: GetCurrencyRateUseCase;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    useCase = new GetCurrencyRateUseCase(relationalDataService);

    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await relationalDataService.flush();
  });

  const testCases: GetCurrencyRateTestCase[] = [
    {
      name: 'должен вернуть курс валюты по дате',
      initRelationalState: {
        currencyRates: [
          {
            date: '2026-01-01',
            rate: {
              USD: 1.0,
              EUR: 0.85,
              RUB: 75.0,
            },
            createdAt: new Date('2026-01-01T00:00:00Z'),
            updatedAt: new Date('2026-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        date: '2026-01-01',
      },
      output: {
        date: '2026-01-01',
        rate: {
          USD: 1.0,
          EUR: 0.85,
          RUB: 75.0,
        },
        createdAt: new Date('2026-01-01T00:00:00Z'),
        updatedAt: new Date('2026-01-01T00:00:00Z'),
      },
    },
    {
      name: 'должен вернуть null когда курс валюты не найден',
      initRelationalState: {},
      input: {
        date: '2026-01-01',
      },
      output: null,
    },
  ];

  testCases.forEach((testCase) => {
    it(testCase.name, async () => {
      await prepareInitRelationalState({
        rDataService: relationalDataService,
        initState: testCase.initRelationalState,
      });

      const result = await useCase.execute(testCase.input);

      expect(result).toEqual(testCase.output);
    });
  });
});
