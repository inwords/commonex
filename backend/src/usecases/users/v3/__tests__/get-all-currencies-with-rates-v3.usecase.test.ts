import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {GetAllCurrenciesWithRatesUseCaseV3} from '../get-all-currencies-with-rates-v3.usecase';
import {TestCase, prepareInitRelationalState} from '../../../__tests__/test-helpers';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {CurrencyCode, ICurrency} from '#domain/entities/currency.entity';
import {ICurrencyRate} from '#domain/entities/currency-rate.entity';
import {CurrencyRateNotFoundError} from '#domain/errors';
import {error, success} from '#packages/result';

jest.mock('#packages/date-utils', () => ({
  getCurrentDateWithoutTimeUTC: jest.fn(),
}));

import {getCurrentDateWithoutTimeUTC} from '#packages/date-utils';

type GetAllCurrenciesWithRatesTestCase = TestCase<GetAllCurrenciesWithRatesUseCaseV3> & {
  mockDate: string;
};

describe('GetAllCurrenciesWithRatesUseCaseV3', () => {
  let relationalDataService: RelationalDataServiceAbstract;
  let useCase: GetAllCurrenciesWithRatesUseCaseV3;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    useCase = new GetAllCurrenciesWithRatesUseCaseV3(relationalDataService);

    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await relationalDataService.flush();
    jest.clearAllMocks();
  });

  const testCurrencies: ICurrency[] = [
    {
      id: 'currency-1',
      code: CurrencyCode.EUR,
      createdAt: new Date('2023-01-01T00:00:00Z'),
      updatedAt: new Date('2023-01-01T00:00:00Z'),
    },
    {
      id: 'currency-2',
      code: CurrencyCode.USD,
      createdAt: new Date('2023-01-01T00:00:00Z'),
      updatedAt: new Date('2023-01-01T00:00:00Z'),
    },
    {
      id: 'currency-3',
      code: CurrencyCode.RUB,
      createdAt: new Date('2023-01-01T00:00:00Z'),
      updatedAt: new Date('2023-01-01T00:00:00Z'),
    },
  ];

  const testCurrencyRate: ICurrencyRate = {
    date: '2026-01-06',
    rate: {
      EUR: 1.0,
      USD: 1.05,
      RUB: 80.5,
    },
    createdAt: new Date('2026-01-06T00:00:00Z'),
    updatedAt: new Date('2026-01-06T00:00:00Z'),
  };

  const testCases: GetAllCurrenciesWithRatesTestCase[] = [
    {
      name: 'должен успешно вернуть все валюты и курсы обмена',
      initRelationalState: {
        currencies: testCurrencies,
        currencyRates: [testCurrencyRate],
      },
      input: undefined,
      output: success({
        currencies: testCurrencies,
        exchangeRate: testCurrencyRate.rate,
      }),
      mockDate: '2026-01-06',
    },
    {
      name: 'должен вернуть ошибку если курс на текущую дату не найден',
      initRelationalState: {
        currencies: testCurrencies,
        currencyRates: [],
      },
      input: undefined,
      output: error(new CurrencyRateNotFoundError()),
      mockDate: '2026-01-06',
    },
    {
      name: 'должен работать когда нет валют в базе данных',
      initRelationalState: {
        currencies: [],
        currencyRates: [testCurrencyRate],
      },
      input: undefined,
      output: success({
        currencies: [],
        exchangeRate: testCurrencyRate.rate,
      }),
      mockDate: '2026-01-06',
    },
    {
      name: 'должен вернуть пустой объект курсов если в currencyRate пустой объект rate',
      initRelationalState: {
        currencies: testCurrencies,
        currencyRates: [
          {
            date: '2026-01-06',
            rate: {},
            createdAt: new Date('2026-01-06T00:00:00Z'),
            updatedAt: new Date('2026-01-06T00:00:00Z'),
          },
        ],
      },
      input: undefined,
      output: success({
        currencies: testCurrencies,
        exchangeRate: {},
      }),
      mockDate: '2026-01-06',
    },
  ];

  testCases.forEach((testCase) => {
    it(testCase.name, async () => {
      (getCurrentDateWithoutTimeUTC as jest.Mock).mockReturnValue(testCase.mockDate);

      await prepareInitRelationalState({
        rDataService: relationalDataService,
        initState: testCase.initRelationalState,
      });

      const result = await useCase.execute();

      expect(result).toEqual(testCase.output);
    });
  });
});
