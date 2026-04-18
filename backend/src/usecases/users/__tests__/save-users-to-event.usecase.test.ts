import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {SaveUsersToEventUseCase} from '../save-users-to-event.usecase';
import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {TestCase, prepareInitRelationalState, validateRelationalStateChanges} from '../../__tests__/test-helpers';
import {Result, error, success} from '#packages/result';
import {EventNotFoundError, EventDeletedError, InvalidPinCodeError} from '#domain/errors/errors';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {EventService} from '#frameworks/event-service/event-service';
import {IdempotencySharedUseCase} from '#usecases/shared/idempotency.usecase';

type SaveUsersToEventTestCase = TestCase<SaveUsersToEventUseCase> & {
  mockEventService: {
    isValidEvent: Result<boolean, EventNotFoundError | EventDeletedError | InvalidPinCodeError>;
  };
  mockIdempotencyUseCase?: {execute: Awaited<ReturnType<SaveUsersToEventUseCase['execute']>>};
};

const SAVE_USERS_URL = '/v1/user/event/event-1/users';

describe('SaveUsersToEventUseCase', () => {
  let relationalDataService: RelationalDataServiceAbstract;
  let useCase: SaveUsersToEventUseCase;
  let eventService: EventServiceAbstract;
  let idempotencySharedUseCase: IdempotencySharedUseCase;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    eventService = new EventService();
    idempotencySharedUseCase = new IdempotencySharedUseCase(relationalDataService);
    useCase = new SaveUsersToEventUseCase(relationalDataService, eventService, idempotencySharedUseCase);

    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await relationalDataService.flush();
    jest.restoreAllMocks();
  });

  const testCases: SaveUsersToEventTestCase[] = [
    {
      name: 'должен успешно сохранить пользователей в событие',
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
        pinCode: '1234',
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
        url: 'url',
      },
      output: success([
        {
          id: expect.any(String),
          eventId: 'event-1',
          name: 'John Doe',
          createdAt: expect.any(Date),
          updatedAt: expect.any(Date),
        },
        {
          id: expect.any(String),
          eventId: 'event-1',
          name: 'Jane Smith',
          createdAt: expect.any(Date),
          updatedAt: expect.any(Date),
        },
      ]),
      relationalStateChanges: {
        userInfos: {
          inserted: [
            {
              id: expect.any(String),
              eventId: 'event-1',
              name: 'John Doe',
              createdAt: expect.any(Date),
              updatedAt: expect.any(Date),
            },
            {
              id: expect.any(String),
              eventId: 'event-1',
              name: 'Jane Smith',
              createdAt: expect.any(Date),
              updatedAt: expect.any(Date),
            },
          ],
        },
      },
      mockEventService: {
        isValidEvent: success(true),
      },
    },
    {
      name: 'повторный запрос с тем же idempotencyKey — возвращает кэш без добавления пользователей',
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
        pinCode: '1234',
        users: [{name: 'John Doe', createdAt: new Date('2023-01-01T00:00:00Z'), updatedAt: new Date('2023-01-01T00:00:00Z')}],
        idempotencyKey: 'idempotency-key-1',
        url: SAVE_USERS_URL,
      },
      output: success([{id: 'cached-user-id', eventId: 'event-1', name: 'John Doe', createdAt: new Date('2026-01-01T00:00:00.000Z'), updatedAt: new Date('2026-01-01T00:00:00.000Z')}]),
      relationalStateChanges: {},
      mockEventService: {isValidEvent: success(true)},
      mockIdempotencyUseCase: {
        execute: success([{id: 'cached-user-id', eventId: 'event-1', name: 'John Doe', createdAt: new Date('2026-01-01T00:00:00.000Z'), updatedAt: new Date('2026-01-01T00:00:00.000Z')}]),
      },
    },
    {
      name: 'должен вернуть ошибку когда события не существует',
      initRelationalState: {},
      input: {
        eventId: 'non-existent',
        pinCode: '1234',
        users: [
          {
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
        url: 'url',
      },
      output: error(new EventNotFoundError()),
      relationalStateChanges: {},
      mockEventService: {
        isValidEvent: error(new EventNotFoundError()),
      },
    },
    {
      name: 'должен вернуть ошибку когда событие удалено',
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
        users: [
          {
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
        url: 'url',
      },
      output: error(new EventDeletedError()),
      relationalStateChanges: {},
      mockEventService: {
        isValidEvent: error(new EventDeletedError()),
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
        users: [
          {
            name: 'John Doe',
            createdAt: new Date('2023-01-01T00:00:00Z'),
            updatedAt: new Date('2023-01-01T00:00:00Z'),
          },
        ],
        url: 'url',
      },
      output: error(new InvalidPinCodeError()),
      relationalStateChanges: {},
      mockEventService: {
        isValidEvent: error(new InvalidPinCodeError()),
      },
    },
  ];

  testCases.forEach((testCase) => {
    it(testCase.name, async () => {
      await prepareInitRelationalState({
        rDataService: relationalDataService,
        initState: testCase.initRelationalState,
      });

      jest.spyOn(eventService, 'isValidEvent').mockReturnValue(testCase.mockEventService.isValidEvent);

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
