import {HttpService} from '@nestjs/axios';

import {ICurrencyRate} from '#domain/entities/currency-rate.entity';

import {CurrencyRateService} from '#frameworks/currency-rate-service/currency-rate-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {truncateAllTables} from '#test-support/db';
import {TestCase} from '#test-support/relational-state';

import {FetchAndSaveCurrencyRateSharedUseCase} from '../../shared/fetch-and-save-currency-rate.usecase';
import {FetchCurrencyRateUseCase} from '../fetch-currency-rate.usecase';

type FetchCurrencyRateTestCase = TestCase<FetchCurrencyRateUseCase> & {
  mockSharedUseCase: {
    execute: ICurrencyRate;
  };
};

describe('FetchCurrencyRateUseCase', () => {
  let relationalDataService: RelationalDataService;
  let useCase: FetchCurrencyRateUseCase;
  let sharedUseCase: FetchAndSaveCurrencyRateSharedUseCase;
  let currencyRateService: CurrencyRateService;
  let httpService: HttpService;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    httpService = {} as HttpService;
    currencyRateService = new CurrencyRateService(httpService);
    sharedUseCase = new FetchAndSaveCurrencyRateSharedUseCase(relationalDataService, currencyRateService);
    useCase = new FetchCurrencyRateUseCase(sharedUseCase);

    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
    jest.clearAllMocks();
  });

  const testCases: FetchCurrencyRateTestCase[] = [
    {
      name: 'fetches the currency rate through the shared use case',
      initRelationalState: {},
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
        createdAt: expect.any(Date),
        updatedAt: expect.any(Date),
      },
      mockSharedUseCase: {
        execute: {
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
    },
  ];

  testCases.forEach((testCase) => {
    it(testCase.name, async () => {
      const executeSpy = jest.spyOn(sharedUseCase, 'execute').mockResolvedValue(testCase.mockSharedUseCase.execute);

      const result = await useCase.execute(testCase.input);

      expect(result).toEqual(testCase.output);
      expect(executeSpy).toHaveBeenCalledWith({date: testCase.input.date});
    });
  });
});
