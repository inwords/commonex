import {createHash} from 'crypto';

import {IdempotencyHashMismatchError} from '#domain/errors/errors';

import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {truncateAllTables} from '#test-support/db';
import {
  RelationalState,
  RelationalStateChanges,
  prepareInitRelationalState,
  useFakeTimers,
  validateRelationalStateChanges,
} from '#test-support/relational-state';

import {IdempotencySharedUseCase} from '../idempotency.usecase';

const KEY = '01JQKP8G0000000000000000AA';
const URL = '/v2/user/event/event-1/expense';
const BODY = {amount: 100};
const FN_RESULT = {id: 'expense-1', amount: 100};
const FAILED_RESULT = {result: 'error', error: {name: 'EventNotFoundError'}};
const TTL_MS = 24 * 60 * 60 * 1000;

const computeHash = (url: string, body: object): string =>
  createHash('sha256').update(JSON.stringify({url, body})).digest('hex');

interface IdempotencyTestCase {
  name: string;
  initRelationalState: RelationalState;
  input: {key: string | undefined; body: object};
  output?: unknown;
  expectError?: new () => Error;
  mockFn: {result: unknown};
  expectedFnCallCount: number;
  relationalStateChanges?: RelationalStateChanges;
}

describe('IdempotencySharedUseCase', () => {
  let relationalDataService: RelationalDataService;
  let useCase: IdempotencySharedUseCase;

  const mockNow = new Date('2026-01-01T00:00:00.000Z');

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: false,
    });

    useCase = new IdempotencySharedUseCase(relationalDataService);

    await relationalDataService.initialize();

    useFakeTimers(mockNow.getTime());
  });

  afterAll(async () => {
    await relationalDataService.destroy();
    jest.useRealTimers();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
    jest.clearAllMocks();
  });

  const testCases: IdempotencyTestCase[] = [
    {
      name: 'без ключа — вызывает fn() и возвращает результат',
      initRelationalState: {},
      input: {key: undefined, body: BODY},
      output: FN_RESULT,
      mockFn: {result: FN_RESULT},
      expectedFnCallCount: 1,
      relationalStateChanges: {},
    },
    {
      name: 'новый ключ — вызывает fn(), сохраняет запись в БД, возвращает результат',
      initRelationalState: {},
      input: {key: KEY, body: BODY},
      output: FN_RESULT,
      mockFn: {result: FN_RESULT},
      expectedFnCallCount: 1,
      relationalStateChanges: {
        idempotencyKeys: {
          inserted: [
            {
              key: KEY,
              url: URL,
              requestHash: computeHash(URL, BODY),
              response: FN_RESULT,
              statusCode: 200,
              expiresAt: new Date(mockNow.getTime() + TTL_MS),
              createdAt: mockNow,
            },
          ],
        },
      },
    },
    {
      name: 'does not store a failed Result so a retry re-runs the operation',
      initRelationalState: {},
      input: {key: KEY, body: BODY},
      output: FAILED_RESULT,
      mockFn: {result: FAILED_RESULT},
      expectedFnCallCount: 1,
      relationalStateChanges: {},
    },
    {
      name: 'повторный ключ с тем же хэшем — возвращает кэш, fn() не вызывается',
      initRelationalState: {
        idempotencyKeys: [
          {
            key: KEY,
            url: URL,
            requestHash: computeHash(URL, BODY),
            response: FN_RESULT,
            statusCode: 200,
            expiresAt: new Date(mockNow.getTime() + TTL_MS),
            createdAt: mockNow,
          },
        ],
      },
      input: {key: KEY, body: BODY},
      output: FN_RESULT,
      mockFn: {result: {id: 'new-result'}},
      expectedFnCallCount: 0,
      relationalStateChanges: {},
    },
    {
      name: 'повторный ключ с другим хэшем — бросает IdempotencyHashMismatchError',
      initRelationalState: {
        idempotencyKeys: [
          {
            key: KEY,
            url: URL,
            requestHash: computeHash(URL, BODY),
            response: FN_RESULT,
            statusCode: 200,
            expiresAt: new Date(mockNow.getTime() + TTL_MS),
            createdAt: mockNow,
          },
        ],
      },
      input: {key: KEY, body: {amount: 999}},
      expectError: IdempotencyHashMismatchError,
      mockFn: {result: FN_RESULT},
      expectedFnCallCount: 0,
      relationalStateChanges: {},
    },
  ];

  testCases.forEach((testCase) => {
    it(testCase.name, async () => {
      await prepareInitRelationalState({
        rDataService: relationalDataService,
        initState: testCase.initRelationalState,
      });

      const fn = jest.fn().mockResolvedValue(testCase.mockFn.result);

      if (testCase.expectError) {
        await expect(useCase.execute(testCase.input.key, URL, testCase.input.body, fn)).rejects.toBeInstanceOf(
          testCase.expectError,
        );
      } else {
        const result = await useCase.execute(testCase.input.key, URL, testCase.input.body, fn);
        expect(result).toEqual(testCase.output);
      }

      expect(fn).toHaveBeenCalledTimes(testCase.expectedFnCallCount);

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
