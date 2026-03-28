import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {FetchDailyCurrencyRatesUseCase} from '../fetch-daily-currency-rates.usecase';
import {FetchAndSaveCurrencyRateSharedUseCase} from '../../shared/fetch-and-save-currency-rate.usecase';
import {CurrencyRateService} from '#frameworks/currency-rate-service/currency-rate-service';
import {TestCase} from '../../__tests__/test-helpers';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {HttpService} from '@nestjs/axios';
import {ICurrencyRate} from '#domain/entities/currency-rate.entity';

jest.mock('#packages/date-utils', () => ({
  getCurrentDateWithoutTimeUTC: jest.fn(),
}));

import {getCurrentDateWithoutTimeUTC} from '#packages/date-utils';

type FetchDailyCurrencyRatesTestCase = TestCase<FetchDailyCurrencyRatesUseCase> & {
  mockDate: string;
  mockSharedUseCase: {
    execute: ICurrencyRate;
  };
};

describe('FetchDailyCurrencyRatesUseCase', () => {
  let relationalDataService: RelationalDataServiceAbstract;
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
    await relationalDataService.flush();
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
      jest.spyOn(sharedUseCase, 'execute').mockResolvedValue(testCase.mockSharedUseCase.execute);

      await useCase.execute();

      expect(getCurrentDateWithoutTimeUTC).toHaveBeenCalled();
      expect(sharedUseCase.execute).toHaveBeenCalledWith({date: testCase.mockDate});
    });
  });
});
