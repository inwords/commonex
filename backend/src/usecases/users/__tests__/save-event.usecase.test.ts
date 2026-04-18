import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {SaveEventUseCase} from '../save-event.usecase';
import {TestCase, prepareInitRelationalState, validateRelationalStateChanges} from '../../__tests__/test-helpers';
import {error, success} from '#packages/result';
import {CurrencyNotFoundError} from '#domain/errors/errors';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {CurrencyCode} from '#domain/entities/currency.entity';
import {SupportedCurrencyService} from '#frameworks/supported-currency-service/supported-currency-service';
import {IdempotencySharedUseCase} from '#usecases/shared/idempotency.usecase';

type SaveEventTestCase = TestCase<SaveEventUseCase> & {
  mockIdempotencyUseCase?: {execute: Awaited<ReturnType<SaveEventUseCase['execute']>>};
};

const SAVE_EVENT_URL = '/v1/user/event';

describe('SaveEventUseCase', () => {
  let relationalDataService: RelationalDataServiceAbstract;
  let useCase: SaveEventUseCase;
  let idempotencySharedUseCase: IdempotencySharedUseCase;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    idempotencySharedUseCase = new IdempotencySharedUseCase(relationalDataService);
    useCase = new SaveEventUseCase(relationalDataService, new SupportedCurrencyService(relationalDataService), idempotencySharedUseCase);

    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await relationalDataService.flush();
    jest.restoreAllMocks();
  });

  const testCases: SaveEventTestCase[] = [
    {
      name: 'должен успешно создать событие с пользователями',
      initRelationalState: {
        currencies: [
          {
            id: 'currency-usd',
            code: CurrencyCode.USD,
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        event: {
          name: 'New Event',
          currencyId: 'currency-usd',
          pinCode: '1234',
        },
        users: [
          {
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
          {
            name: 'Jane Smith',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      output: success({
        id: expect.any(String),
        name: 'New Event',
        currencyId: 'currency-usd',
        pinCode: '1234',
        createdAt: expect.any(Date),
        updatedAt: expect.any(Date),
        deletedAt: null,
        users: [
          {
            id: expect.any(String),
            eventId: expect.any(String),
            name: 'John Doe',
            createdAt: expect.any(Date),
            updatedAt: expect.any(Date),
          },
          {
            id: expect.any(String),
            eventId: expect.any(String),
            name: 'Jane Smith',
            createdAt: expect.any(Date),
            updatedAt: expect.any(Date),
          },
        ],
      }),
      relationalStateChanges: {
        events: {
          inserted: [
            {
              id: expect.any(String),
              name: 'New Event',
              currencyId: 'currency-usd',
              pinCode: '1234',
              createdAt: expect.any(Date),
              updatedAt: expect.any(Date),
              deletedAt: null,
            },
          ],
        },
        userInfos: {
          inserted: [
            {
              id: expect.any(String),
              eventId: expect.any(String),
              name: 'John Doe',
              createdAt: expect.any(Date),
              updatedAt: expect.any(Date),
            },
            {
              id: expect.any(String),
              eventId: expect.any(String),
              name: 'Jane Smith',
              createdAt: expect.any(Date),
              updatedAt: expect.any(Date),
            },
          ],
        },
      },
    },
    {
      name: 'повторный запрос с тем же idempotencyKey — возвращает кэш без создания события',
      initRelationalState: {},
      input: {
        event: {name: 'New Event', currencyId: 'currency-usd', pinCode: '1234'},
        users: [{name: 'John Doe', createdAt: new Date('2023-01-01T00:00:00Z'), updatedAt: new Date('2023-01-01T00:00:00Z')}],
        idempotencyKey: 'idempotency-key-1',
        url: SAVE_EVENT_URL,
      },
      output: success({
        id: 'cached-event-id',
        name: 'New Event',
        currencyId: 'currency-usd',
        pinCode: '1234',
        createdAt: new Date('2026-01-01T00:00:00.000Z'),
        updatedAt: new Date('2026-01-01T00:00:00.000Z'),
        deletedAt: null,
        users: [],
      }),
      relationalStateChanges: {},
      mockIdempotencyUseCase: {
        execute: success({
          id: 'cached-event-id',
          name: 'New Event',
          currencyId: 'currency-usd',
          pinCode: '1234',
          createdAt: new Date('2026-01-01T00:00:00.000Z'),
          updatedAt: new Date('2026-01-01T00:00:00.000Z'),
          deletedAt: null,
          users: [],
        }),
      },
    },
    {
      name: 'должен вернуть ошибку когда валюта не найдена',
      initRelationalState: {},
      input: {
        event: {
          name: 'New Event',
          currencyId: 'non-existent',
          pinCode: '1234',
        },
        users: [
          {
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      output: error(new CurrencyNotFoundError()),
      relationalStateChanges: {},
    },
    {
      name: 'должен вернуть ошибку когда валюта существует в базе, но еще не поддерживается',
      initRelationalState: {
        currencies: [
          {
            id: 'currency-zzz',
            code: 'ZZZ' as CurrencyCode,
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        event: {
          name: 'New Event',
          currencyId: 'currency-zzz',
          pinCode: '1234',
        },
        users: [
          {
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      output: error(new CurrencyNotFoundError()),
      relationalStateChanges: {},
    },
  ];

  testCases.forEach((testCase) => {
    it(testCase.name, async () => {
      await prepareInitRelationalState({
        rDataService: relationalDataService,
        initState: testCase.initRelationalState,
      });

      if (testCase.mockIdempotencyUseCase) {
        const {execute} = testCase.mockIdempotencyUseCase;
        jest.spyOn(idempotencySharedUseCase, 'execute').mockReturnValue(Promise.resolve(execute));
      }

      const result = await useCase.execute(testCase.input);

      expect(result).toEqual(testCase.output);

      if (testCase.relationalStateChanges) {
        await validateRelationalStateChanges({
          rDataService: relationalDataService,
          initState: testCase.initRelationalState,
          stateChanges: testCase.relationalStateChanges,
        });
      }
    });
  });
});
