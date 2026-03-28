import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {prepareInitRelationalState, TestCase} from '../../../__tests__/test-helpers';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {ICurrencyRate} from '#domain/entities/currency-rate.entity';
import {CurrencyCode} from '#domain/entities/currency.entity';
import {CurrencyRateNotFoundError} from '#domain/errors';
import {error, isSuccess, success} from '#packages/result';
import {buildCurrenciesV3Version, buildCurrenciesV3WeakEtag} from '../currencies-v3-cache';
import {getCurrentDateWithoutTimeUTC} from '#packages/date-utils';
import {GetAllCurrenciesWithRatesUseCaseV3, GetCurrenciesV3VersionUseCase} from '#usecases/users/v3';
import {SupportedCurrencyService} from '#frameworks/supported-currency-service/supported-currency-service';

jest.mock('#packages/date-utils', () => ({
  getCurrentDateWithoutTimeUTC: jest.fn(),
}));

type GetCurrenciesV3VersionTestCase = TestCase<GetCurrenciesV3VersionUseCase> & {
  mockDate: string;
};

describe('GetCurrenciesV3VersionUseCase', () => {
  let relationalDataService: RelationalDataServiceAbstract;
  let versionUseCase: GetCurrenciesV3VersionUseCase;
  let fullUseCase: GetAllCurrenciesWithRatesUseCaseV3;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    const supportedCurrencyService = new SupportedCurrencyService(relationalDataService);
    versionUseCase = new GetCurrenciesV3VersionUseCase(supportedCurrencyService);
    fullUseCase = new GetAllCurrenciesWithRatesUseCaseV3(supportedCurrencyService);

    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await relationalDataService.flush();
    jest.clearAllMocks();
  });

  const testCurrencyRate: ICurrencyRate = {
    date: '2026-01-06',
    rate: {
      EUR: 1.0,
      USD: 1.05,
      RUB: 80.5,
      ZZZ: 999,
    },
    createdAt: new Date('2026-01-06T00:00:00Z'),
    updatedAt: new Date('2026-01-06T12:00:00Z'),
  };

  const testCases: GetCurrenciesV3VersionTestCase[] = [
    {
      name: 'должен вернуть версию по updatedAt поддерживаемых валют и совпасть с полной выдачей',
      initRelationalState: {
        currencies: [
          {
            id: 'currency-usd',
            code: CurrencyCode.USD,
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-03T00:00:00Z'),
          },
          {
            id: 'currency-zzz',
            code: 'ZZZ' as CurrencyCode,
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-02-01T00:00:00Z'),
          },
          {
            id: 'currency-eur',
            code: CurrencyCode.EUR,
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
          {
            id: 'currency-rub',
            code: CurrencyCode.RUB,
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-02T00:00:00Z'),
          },
        ],
        currencyRates: [testCurrencyRate],
      },
      input: undefined,
      output: success(
        buildCurrenciesV3Version({
          rateUpdatedAt: testCurrencyRate.updatedAt,
          currenciesUpdatedAt: [
            new Date('2023-01-01T00:00:00Z'),
            new Date('2023-01-02T00:00:00Z'),
            new Date('2023-01-03T00:00:00Z'),
          ],
        }),
      ),
      mockDate: '2026-01-06',
    },
    {
      name: 'должен вернуть пустой список updatedAt когда таблица валют пуста',
      initRelationalState: {
        currencies: [],
        currencyRates: [testCurrencyRate],
      },
      input: undefined,
      output: success(
        buildCurrenciesV3Version({
          rateUpdatedAt: testCurrencyRate.updatedAt,
          currenciesUpdatedAt: [],
        }),
      ),
      mockDate: '2026-01-06',
    },
    {
      name: 'должен вернуть ошибку если курс на текущую дату не найден',
      initRelationalState: {
        currencies: [],
        currencyRates: [],
      },
      input: undefined,
      output: error(new CurrencyRateNotFoundError()),
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

      const versionResult = await versionUseCase.execute();

      expect(versionResult).toEqual(testCase.output);

      if (isSuccess(versionResult)) {
        const fullResult = await fullUseCase.execute();

        expect(isSuccess(fullResult)).toBe(true);
        if (!isSuccess(fullResult)) {
          throw new Error('Expected full currencies use case to succeed');
        }

        expect(fullResult.value.version).toEqual(versionResult.value);
        expect(buildCurrenciesV3WeakEtag(fullResult.value.version)).toBe(buildCurrenciesV3WeakEtag(versionResult.value));
      }
    });
  });
});
