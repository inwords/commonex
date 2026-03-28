import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {GetAllCurrenciesUseCase} from '../get-all-currencies.usecase';
import {TestCase, prepareInitRelationalState} from '../../__tests__/test-helpers';
import {success} from '#packages/result';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {CurrencyCode} from '#domain/entities/currency.entity';
import {SupportedCurrencyService} from '#frameworks/supported-currency-service/supported-currency-service';

type GetAllCurrenciesTestCase = TestCase<GetAllCurrenciesUseCase>;

describe('GetAllCurrenciesUseCase', () => {
  let relationalDataService: RelationalDataServiceAbstract;
  let useCase: GetAllCurrenciesUseCase;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    useCase = new GetAllCurrenciesUseCase(new SupportedCurrencyService(relationalDataService));

    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await relationalDataService.flush();
  });

  const testCases: GetAllCurrenciesTestCase[] = [
    {
      name: 'должен вернуть только валюты из списка поддерживаемых кодов',
      initRelationalState: {
        currencies: [
          {
            id: 'currency-usd',
            code: CurrencyCode.USD,
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
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
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
          {
            id: 'currency-zzz',
            code: 'ZZZ' as CurrencyCode,
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-02-01T00:00:00Z'),
          },
        ],
      },
      input: undefined,
      output: success([
        {
          id: 'currency-usd',
          code: CurrencyCode.USD,
          createdAt: new Date('2023-01-01T00:00:00Z'),
          updatedAt: new Date('2023-01-01T00:00:00Z'),
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
          updatedAt: new Date('2023-01-01T00:00:00Z'),
        },
      ]),
    },
    {
      name: 'должен вернуть пустой массив когда валют нет',
      initRelationalState: {},
      input: undefined,
      output: success([]),
    },
  ];

  testCases.forEach((testCase) => {
    it(testCase.name, async () => {
      await prepareInitRelationalState({
        rDataService: relationalDataService,
        initState: testCase.initRelationalState,
      });

      const result = await useCase.execute();

      expect(result).toEqual(testCase.output);
    });
  });
});
