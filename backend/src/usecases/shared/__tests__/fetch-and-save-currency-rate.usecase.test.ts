import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {FetchAndSaveCurrencyRateSharedUseCase} from '../fetch-and-save-currency-rate.usecase';
import {CurrencyRateService} from '#frameworks/currency-rate-service/currency-rate-service';
import {TestCase, prepareInitRelationalState, validateRelationalStateChanges} from '../../__tests__/test-helpers';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {HttpService} from '@nestjs/axios';

type FetchAndSaveCurrencyRateTestCase = TestCase<FetchAndSaveCurrencyRateSharedUseCase> & {
  mockCurrencyRateService: {
    getCurrencyRate: Record<string, number> | null;
  };
  expectError?: boolean;
};

describe('FetchAndSaveCurrencyRateSharedUseCase', () => {
  let relationalDataService: RelationalDataServiceAbstract;
  let useCase: FetchAndSaveCurrencyRateSharedUseCase;
  let currencyRateService: CurrencyRateService;
  let httpService: HttpService;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    httpService = {} as HttpService;
    currencyRateService = new CurrencyRateService(httpService);
    useCase = new FetchAndSaveCurrencyRateSharedUseCase(relationalDataService, currencyRateService);

    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await relationalDataService.flush();
    jest.clearAllMocks();
  });

  const testCases: FetchAndSaveCurrencyRateTestCase[] = [
    {
      name: 'должен успешно получить и сохранить курс валюты',
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
      relationalStateChanges: {
        currencyRates: {
          inserted: [
            {
              date: '2026-01-01',
              rate: {
                USD: 1.0,
                EUR: 0.85,
                RUB: 75.0,
              },
              createdAt: expect.any(Date),
              updatedAt: expect.any(Date),
            },
          ],
        },
      },
      mockCurrencyRateService: {
        getCurrencyRate: {
          USD: 1.0,
          EUR: 0.85,
          RUB: 75.0,
        },
      },
    },
    {
      name: 'должен выбросить ошибку когда не удалось получить курсы валют',
      initRelationalState: {},
      input: {
        date: '2026-01-01',
      },
      output: undefined as any,
      expectError: true,
      mockCurrencyRateService: {
        getCurrencyRate: null,
      },
    },
  ];

  testCases.forEach((testCase) => {
    it(testCase.name, async () => {
      await prepareInitRelationalState({
        rDataService: relationalDataService,
        initState: testCase.initRelationalState,
      });

      jest
        .spyOn(currencyRateService, 'getCurrencyRate')
        .mockResolvedValue(testCase.mockCurrencyRateService.getCurrencyRate);

      if (testCase.expectError) {
        await expect(useCase.execute(testCase.input)).rejects.toThrow();
      } else {
        const result = await useCase.execute(testCase.input);

        expect(result).toEqual(testCase.output);

        if (testCase.relationalStateChanges) {
          await validateRelationalStateChanges({
            rDataService: relationalDataService,
            initState: testCase.initRelationalState,
            stateChanges: testCase.relationalStateChanges,
          });
        }
      }
    });
  });
});
