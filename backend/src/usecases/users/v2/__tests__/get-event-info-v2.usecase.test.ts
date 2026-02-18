import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {GetEventInfoV2UseCase} from '#usecases/users/v2';
import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {prepareInitRelationalState, TestCase, useFakeTimers} from '../../../__tests__/test-helpers';
import {error, Result, success} from '#packages/result';
import {EventDeletedError, EventNotFoundError, InvalidPinCodeError, InvalidTokenError, TokenExpiredError,} from '#domain/errors/errors';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {EventService} from '#frameworks/event-service/event-service';

type GetEventInfoV2TestCase = TestCase<GetEventInfoV2UseCase> & {
  mockEventService?: {
    isEventExists?: boolean;
    isEventNotDeleted?: Result<boolean, EventDeletedError>;
    isValidPinCode?: Result<boolean, InvalidPinCodeError>;
  };
};

describe('GetEventInfoV2UseCase', () => {
  let relationalDataService: RelationalDataServiceAbstract;
  let useCase: GetEventInfoV2UseCase;
  let eventService: EventServiceAbstract;

  const mockNow = new Date('2026-01-01T00:00:00.000Z');

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    eventService = new EventService();
    useCase = new GetEventInfoV2UseCase(relationalDataService, eventService);

    await relationalDataService.initialize();

    useFakeTimers(mockNow.getTime());
  });

  afterAll(async () => {
    await relationalDataService.destroy();
    jest.useRealTimers();
  });

  beforeEach(async () => {
    await relationalDataService.flush();
    jest.clearAllMocks();
  });

  const testCases: GetEventInfoV2TestCase[] = [
    {
      name: 'должен вернуть информацию о событии с валидным pinCode',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-1',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
        userInfos: [
          {
            id: 'user-1',
            eventId: 'event-1',
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
          {
            id: 'user-2',
            eventId: 'event-1',
            name: 'Jane Smith',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        pinCode: '1234',
      },
      output: success({
        id: 'event-1',
        name: 'Test Event',
        currencyId: 'currency-1',
        pinCode: '1234',
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
        deletedAt: null,
        users: [
          {
            id: 'user-1',
            eventId: 'event-1',
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
          {
            id: 'user-2',
            eventId: 'event-1',
            name: 'Jane Smith',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      }),
      mockEventService: {
        isEventExists: true,
        isEventNotDeleted: success(true),
        isValidPinCode: success(true),
      },
    },
    {
      name: 'должен вернуть информацию о событии с валидным токеном',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-1',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
        userInfos: [
          {
            id: 'user-1',
            eventId: 'event-1',
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
        eventShareTokens: [
          {
            token: 'valid-token-123',
            eventId: 'event-1',
            expiresAt: new Date('2026-12-31T00:00:00Z'),
            createdAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        token: 'valid-token-123',
      },
      output: success({
        id: 'event-1',
        name: 'Test Event',
        currencyId: 'currency-1',
        pinCode: '1234',
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
        deletedAt: null,
        users: [
          {
            id: 'user-1',
            eventId: 'event-1',
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      }),
      mockEventService: {
        isEventExists: true,
        isEventNotDeleted: success(true),
      },
    },
    {
      name: 'должен вернуть ошибку когда события не существует (с pinCode)',
      initRelationalState: {},
      input: {
        eventId: 'non-existent',
        pinCode: '1234',
      },
      output: error(new EventNotFoundError()),
      mockEventService: {
        isEventExists: false,
      },
    },
    {
      name: 'должен вернуть ошибку когда событие удалено (с pinCode)',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-1',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: new Date('2023-01-02T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        pinCode: '1234',
      },
      output: error(new EventDeletedError()),
      mockEventService: {
        isEventExists: true,
        isEventNotDeleted: error(new EventDeletedError()),
      },
    },
    {
      name: 'должен вернуть ошибку когда pin код неверный',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-1',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
      },
      input: {
        eventId: 'event-1',
        pinCode: 'wrong',
      },
      output: error(new InvalidPinCodeError()),
      mockEventService: {
        isEventExists: true,
        isEventNotDeleted: success(true),
        isValidPinCode: error(new InvalidPinCodeError()),
      },
    },
    {
      name: 'должен вернуть ошибку когда токен не найден',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-1',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
      },
      input: {
        eventId: 'event-1',
        token: 'invalid-token',
      },
      output: error(new InvalidTokenError()),
      mockEventService: {
        isEventExists: true,
        isEventNotDeleted: success(true),
      },
    },
    {
      name: 'должен вернуть ошибку когда токен не совпадает с eventId',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-1',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
        eventShareTokens: [
          {
            token: 'valid-token-123',
            eventId: 'event-2',
            expiresAt: new Date('2026-12-31T00:00:00Z'),
            createdAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        token: 'valid-token-123',
      },
      output: error(new InvalidTokenError()),
      mockEventService: {
        isEventExists: true,
        isEventNotDeleted: success(true),
      },
    },
    {
      name: 'должен вернуть ошибку когда токен истек',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-1',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
        eventShareTokens: [
          {
            token: 'expired-token-123',
            eventId: 'event-1',
            expiresAt: new Date('2025-12-31T00:00:00Z'),
            createdAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        token: 'expired-token-123',
      },
      output: error(new TokenExpiredError()),
      mockEventService: {
        isEventExists: true,
        isEventNotDeleted: success(true),
      },
    },
    {
      name: 'должен вернуть ошибку когда события не существует (с токеном)',
      initRelationalState: {},
      input: {
        eventId: 'non-existent',
        token: 'some-token',
      },
      output: error(new EventNotFoundError()),
      mockEventService: {
        isEventExists: false,
      },
    },
    {
      name: 'должен вернуть ошибку когда событие удалено (с токеном)',
      initRelationalState: {
        events: [
          {
            id: 'event-1',
            name: 'Test Event',
            currencyId: 'currency-1',
            pinCode: '1234',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
            deletedAt: new Date('2023-01-02T00:00:00Z'),
          },
        ],
        eventShareTokens: [
          {
            token: 'valid-token-123',
            eventId: 'event-1',
            expiresAt: new Date('2026-12-31T00:00:00Z'),
            createdAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
      },
      input: {
        eventId: 'event-1',
        token: 'valid-token-123',
      },
      output: error(new EventDeletedError()),
      mockEventService: {
        isEventExists: true,
        isEventNotDeleted: error(new EventDeletedError()),
      },
    },
  ];

  testCases.forEach((testCase) => {
    it(testCase.name, async () => {
      await prepareInitRelationalState({
        rDataService: relationalDataService,
        initState: testCase.initRelationalState,
      });

      if (testCase.mockEventService) {
        if (testCase.mockEventService.isEventExists !== undefined) {
          jest.spyOn(eventService, 'isEventExists').mockReturnValue(testCase.mockEventService.isEventExists);
        }
        if (testCase.mockEventService.isEventNotDeleted) {
          jest.spyOn(eventService, 'isEventNotDeleted').mockReturnValue(testCase.mockEventService.isEventNotDeleted);
        }
        if (testCase.mockEventService.isValidPinCode) {
          jest.spyOn(eventService, 'isValidPinCode').mockReturnValue(testCase.mockEventService.isValidPinCode);
        }
      }

      const result = await useCase.execute(testCase.input);

      expect(result).toEqual(testCase.output);
    });
  });
});
