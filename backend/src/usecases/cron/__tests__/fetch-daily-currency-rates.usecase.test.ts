import {HttpService} from '@nestjs/axios';

import {getCurrentDateWithoutTimeUTC} from '#packages/date-utils';

import {ICurrencyRate} from '#domain/entities/currency-rate.entity';

import {CurrencyRateService} from '#frameworks/currency-rate-service/currency-rate-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {truncateAllTables} from '#test-support/db';
import {TestCase} from '#test-support/relational-state';

import {FetchAndSaveCurrencyRateSharedUseCase} from '../../shared/fetch-and-save-currency-rate.usecase';
import {FetchDailyCurrencyRatesUseCase} from '../fetch-daily-currency-rates.usecase';

jest.mock('#packages/date-utils', () => ({
  getCurrentDateWithoutTimeUTC: jest.fn(),
}));

type FetchDailyCurrencyRatesTestCase = TestCase<FetchDailyCurrencyRatesUseCase> & {
  mockDate: string;
  mockSharedUseCase: {
    execute: ICurrencyRate;
  };
};

describe('FetchDailyCurrencyRatesUseCase', () => {
  let relationalDataService: RelationalDataService;
  let useCase: FetchDailyCurrencyRatesUseCase;
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
    useCase = new FetchDailyCurrencyRatesUseCase(sharedUseCase);

    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
    jest.clearAllMocks();
  });

  const testCases: FetchDailyCurrencyRatesTestCase[] = [
    {
      name: 'должен успешно получить курсы валют на текущую дату',
      initRelationalState: {},
      input: undefined,
      output: undefined,
      mockDate: '2026-01-06',
      mockSharedUseCase: {
        execute: {
          date: '2026-01-06',
          rate: {
            USD: 1.0,
            EUR: 0.85,
            RUB: 75.0,
          },
          createdAt: new Date('2026-01-06T00:00:00Z'),
          updatedAt: new Date('2026-01-06T00:00:00Z'),
        },
      },
    },
  ];

  testCases.forEach((testCase) => {
    it(testCase.name, async () => {
      (getCurrentDateWithoutTimeUTC as jest.Mock).mockReturnValue(testCase.mockDate);
      const executeSpy = jest.spyOn(sharedUseCase, 'execute').mockResolvedValue(testCase.mockSharedUseCase.execute);

      await useCase.execute();

      expect(getCurrentDateWithoutTimeUTC).toHaveBeenCalled();
      expect(executeSpy).toHaveBeenCalledWith({date: testCase.mockDate});
    });
  });
});
