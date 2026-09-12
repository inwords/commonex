import {getCurrentDateWithoutTimeUTC} from '#packages/date-utils';
import {error, isSuccess, success} from '#packages/result';

import {ICurrencyRate} from '#domain/entities/currency-rate.entity';
import {CurrencyCode, ICurrency} from '#domain/entities/currency.entity';
import {CurrencyRateNotFoundError} from '#domain/errors';

import {GetAllCurrenciesWithRatesUseCaseV3} from '#usecases/users/v3';

import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {SupportedCurrencyService} from '#frameworks/supported-currency-service/supported-currency-service';

import {truncateAllTables} from '#test-support/db';
import {TestCase, prepareInitRelationalState} from '#test-support/relational-state';

import {buildCurrenciesV3Version} from '../currencies-v3-cache';

jest.mock('#packages/date-utils', () => ({
  getCurrentDateWithoutTimeUTC: jest.fn(),
}));

type GetAllCurrenciesWithRatesTestCase = TestCase<GetAllCurrenciesWithRatesUseCaseV3> & {
  mockDate: string;
  expectedCurrenciesCount?: number;
};

describe('GetAllCurrenciesWithRatesUseCaseV3', () => {
  let relationalDataService: RelationalDataService;
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
    await truncateAllTables(relationalDataService.dataSource);
    jest.clearAllMocks();
  });

  const testCurrencies: [ICurrency, ICurrency, ICurrency] = [
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
  const shuffledTestCurrencies: ICurrency[] = [testCurrencies[1], testCurrencies[2], testCurrencies[0]];
  const unsupportedCurrency: ICurrency = {
    id: 'currency-zzz',
    code: 'ZZZ' as CurrencyCode,
    createdAt: new Date('2023-01-01T00:00:00Z'),
    updatedAt: new Date('2023-02-01T00:00:00Z'),
  };

  const testCases: GetAllCurrenciesWithRatesTestCase[] = [
    {
      name: 'returns only supported currencies and their exchange rates',
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
      name: 'returns CurrencyRateNotFoundError when no rate exists for the current date',
      initRelationalState: {
        currencies: testCurrencies,
        currencyRates: [],
      },
      input: undefined,
      output: error(new CurrencyRateNotFoundError()),
      mockDate: '2026-01-06',
    },
    {
      name: 'returns an empty currency list with the exchange rate when the database has no currencies',
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
      name: 'returns an empty rate object when currencyRate has an empty rate object',
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
