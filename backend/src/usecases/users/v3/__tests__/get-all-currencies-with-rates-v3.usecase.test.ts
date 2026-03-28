import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {GetAllCurrenciesWithRatesUseCaseV3} from '#usecases/users/v3';
import {prepareInitRelationalState, TestCase} from '../../../__tests__/test-helpers';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {CurrencyCode, ICurrency} from '#domain/entities/currency.entity';
import {ICurrencyRate} from '#domain/entities/currency-rate.entity';
import {CurrencyRateNotFoundError} from '#domain/errors';
import {error, isSuccess, success} from '#packages/result';
import {getCurrentDateWithoutTimeUTC} from '#packages/date-utils';
import {SupportedCurrencyService} from '#frameworks/supported-currency-service/supported-currency-service';
import {buildCurrenciesV3Version} from '../currencies-v3-cache';

jest.mock('#packages/date-utils', () => ({
  getCurrentDateWithoutTimeUTC: jest.fn(),
}));

type GetAllCurrenciesWithRatesTestCase = TestCase<GetAllCurrenciesWithRatesUseCaseV3> & {
  mockDate: string;
  expectedCurrenciesCount?: number;
};

describe('GetAllCurrenciesWithRatesUseCaseV3', () => {
  let relationalDataService: RelationalDataServiceAbstract;
  let useCase: GetAllCurrenciesWithRatesUseCaseV3;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    useCase = new GetAllCurrenciesWithRatesUseCaseV3(new SupportedCurrencyService(relationalDataService));

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
      ZZZ: 999,
    },
    createdAt: new Date('2026-01-06T00:00:00Z'),
    updatedAt: new Date('2026-01-06T00:00:00Z'),
  };
  const shuffledTestCurrencies: ICurrency[] = [testCurrencies[1]!, testCurrencies[2]!, testCurrencies[0]!];
  const unsupportedCurrency: ICurrency = {
    id: 'currency-zzz',
    code: 'ZZZ' as CurrencyCode,
    createdAt: new Date('2023-01-01T00:00:00Z'),
    updatedAt: new Date('2023-02-01T00:00:00Z'),
  };

  const testCases: GetAllCurrenciesWithRatesTestCase[] = [
    {
      name: 'должен успешно вернуть только поддерживаемые валюты и курсы обмена',
      initRelationalState: {
        currencies: [...shuffledTestCurrencies, unsupportedCurrency],
        currencyRates: [testCurrencyRate],
      },
      input: undefined,
      output: success({
        response: {
          currencies: [
            {
              id: 'currency-1',
              code: CurrencyCode.EUR,
              updatedAt: new Date('2023-01-01T00:00:00Z'),
            },
            {
              id: 'currency-3',
              code: CurrencyCode.RUB,
              updatedAt: new Date('2023-01-01T00:00:00Z'),
            },
            {
              id: 'currency-2',
              code: CurrencyCode.USD,
              updatedAt: new Date('2023-01-01T00:00:00Z'),
            },
          ],
          exchangeRate: {
            EUR: 1.0,
            USD: 1.05,
            RUB: 80.5,
          },
        },
        version: buildCurrenciesV3Version({
          rateUpdatedAt: testCurrencyRate.updatedAt,
          currenciesUpdatedAt: [
            new Date('2023-01-01T00:00:00Z'),
            new Date('2023-01-01T00:00:00Z'),
            new Date('2023-01-01T00:00:00Z'),
          ],
        }),
      }),
      mockDate: '2026-01-06',
      expectedCurrenciesCount: testCurrencies.length,
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
        response: {
          currencies: [],
          exchangeRate: {
            EUR: 1.0,
            USD: 1.05,
            RUB: 80.5,
          },
        },
        version: buildCurrenciesV3Version({
          rateUpdatedAt: testCurrencyRate.updatedAt,
          currenciesUpdatedAt: [],
        }),
      }),
      mockDate: '2026-01-06',
      expectedCurrenciesCount: 0,
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
        response: {
          currencies: [
            {
              id: 'currency-1',
              code: CurrencyCode.EUR,
              updatedAt: new Date('2023-01-01T00:00:00Z'),
            },
            {
              id: 'currency-3',
              code: CurrencyCode.RUB,
              updatedAt: new Date('2023-01-01T00:00:00Z'),
            },
            {
              id: 'currency-2',
              code: CurrencyCode.USD,
              updatedAt: new Date('2023-01-01T00:00:00Z'),
            },
          ],
          exchangeRate: {},
        },
        version: buildCurrenciesV3Version({
          rateUpdatedAt: new Date('2026-01-06T00:00:00Z'),
          currenciesUpdatedAt: [
            new Date('2023-01-01T00:00:00Z'),
            new Date('2023-01-01T00:00:00Z'),
            new Date('2023-01-01T00:00:00Z'),
          ],
        }),
      }),
      mockDate: '2026-01-06',
      expectedCurrenciesCount: testCurrencies.length,
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

      if (testCase.expectedCurrenciesCount != null && isSuccess(result)) {
        expect(result.value.response.currencies).toHaveLength(testCase.expectedCurrenciesCount);
        expect(result.value.response.currencies.some((currency) => 'createdAt' in currency)).toBe(false);
      }
    });
  });
});
