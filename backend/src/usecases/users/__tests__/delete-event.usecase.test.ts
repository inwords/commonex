import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {DeleteEventUseCase} from '../delete-event.usecase';
import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {prepareInitRelationalState, TestCase, useFakeTimers, validateRelationalStateChanges,} from '../../__tests__/test-helpers';
import {error, Result, success} from '#packages/result';
import {EventDeletedError, EventNotFoundError, InvalidPinCodeError} from '#domain/errors/errors';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {EventService} from '#frameworks/event-service/event-service';
import { IdempotencySharedUseCase } from "#usecases/shared/idempotency.usecase";

type DeleteEventTestCase = TestCase<DeleteEventUseCase> & {
  mockEventService: {
    isValidEvent: Result<boolean, EventNotFoundError | EventDeletedError | InvalidPinCodeError>;
  };
  mockIdempotencyUseCase?: {execute: Awaited<ReturnType<DeleteEventUseCase['execute']>>};
};

const DELETE_EVENT_URL = '/v1/user/event/event-1';

describe('DeleteEventUseCase', () => {
  let relationalDataService: RelationalDataServiceAbstract;
  let useCase: DeleteEventUseCase;
  let eventService: EventServiceAbstract;
  let idempotencySharedUseCase: IdempotencySharedUseCase;

  const mockNow = new Date('2026-01-01T00:00:00.000Z');

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    eventService = new EventService();
    idempotencySharedUseCase = new IdempotencySharedUseCase(relationalDataService);
    useCase = new DeleteEventUseCase(relationalDataService, eventService, idempotencySharedUseCase);

    await relationalDataService.initialize();

    useFakeTimers(mockNow.getTime());
  });

  afterAll(async () => {
    await relationalDataService.destroy();
    jest.useRealTimers();
  });

  beforeEach(async () => {
    await relationalDataService.flush();
    jest.restoreAllMocks();
  });

  const testCases: DeleteEventTestCase[] = [
    {
      name: 'должен успешно удалить событие',
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
      },
      output: success({
        id: 'event-1',
        deletedAt: mockNow,
      }),
      relationalStateChanges: {
        events: {
          updated: [
            {
              where: {id: 'event-1'},
              newData: {
                deletedAt: mockNow,
                updatedAt: mockNow,
              },
            },
          ],
        },
      },
      mockEventService: {
        isValidEvent: success(true),
      },
    },
    {
      name: 'должен вернуть ошибку когда события не существует',
      initRelationalState: {},
      input: {
        eventId: 'non-existent',
        pinCode: '1234',
      },
      output: error(new EventNotFoundError()),
      relationalStateChanges: {},
      mockEventService: {
        isValidEvent: error(new EventNotFoundError()),
      },
    },
    {
      name: 'должен вернуть ошибку когда событие уже удалено',
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
      relationalStateChanges: {},
      mockEventService: {
        isValidEvent: error(new EventDeletedError()),
      },
    },
    {
      name: 'повторный запрос с тем же idempotencyKey — возвращает кэш без удаления события',
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
        idempotencyKey: 'idempotency-key-1',
        url: DELETE_EVENT_URL,
      },
      output: success({id: 'event-1', deletedAt: mockNow}),
      relationalStateChanges: {},
      mockEventService: {
        isValidEvent: success(true),
      },
      mockIdempotencyUseCase: {
        execute: success({id: 'event-1', deletedAt: mockNow}),
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
