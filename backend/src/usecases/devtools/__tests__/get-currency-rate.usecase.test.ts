import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {truncateAllTables} from '#test-support/db';
import {TestCase, prepareInitRelationalState} from '#test-support/relational-state';

import {GetCurrencyRateUseCase} from '../get-currency-rate.usecase';

type GetCurrencyRateTestCase = TestCase<GetCurrencyRateUseCase>;

describe('GetCurrencyRateUseCase', () => {
  let relationalDataService: RelationalDataService;
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
    await truncateAllTables(relationalDataService.dataSource);
  });

  const testCases: GetCurrencyRateTestCase[] = [
    {
      name: 'returns the currency rate for the date',
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
      name: 'returns null when no rate exists for the date',
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
