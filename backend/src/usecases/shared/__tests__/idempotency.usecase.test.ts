import {createHash} from 'crypto';

import {error, isError, success} from '#packages/result';

import {ITransaction} from '#domain/abstracts/relational-data-service/types';
import {IdempotencyHashMismatchError, IdempotencyRequestInProgressError} from '#domain/errors';

import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {truncateAllTables} from '#test-support/db';
import {useFakeTimers} from '#test-support/relational-state';

import {IdempotencyOperation, IdempotencySharedUseCase} from '../idempotency.usecase';

const KEY = '01JQKP8G0000000000000000AA';
const BODY = {amount: 100};
const VALUE = {id: 'expense-1', amount: 100};
const OPERATION = IdempotencyOperation.CREATE_EVENT_EXPENSE_V2;

describe('IdempotencySharedUseCase', () => {
  let relationalDataService: RelationalDataService;
  let useCase: IdempotencySharedUseCase;

  const mockNow = new Date('2026-01-01T00:00:00.000Z');

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({dbConfig: appDbConfig, showQueryDetails: false});
    useCase = new IdempotencySharedUseCase(relationalDataService);
    await relationalDataService.initialize();
    useFakeTimers(mockNow.getTime());
  });

  afterAll(async () => {
    await relationalDataService.destroy();
    jest.useRealTimers();
  });

  beforeEach(async () => {
    jest.setSystemTime(mockNow);
    await truncateAllTables(relationalDataService.dataSource);
    jest.clearAllMocks();
  });

  it('executes the operation in a transaction when no key is given', async () => {
    const operation = jest.fn((trx: ITransaction) => {
      expect(trx.ctx).toBeDefined();
      return Promise.resolve(success(VALUE));
    });

    await expect(useCase.execute(undefined, OPERATION, BODY, operation)).resolves.toEqual(success(VALUE));
    expect(operation).toHaveBeenCalledTimes(1);
  });

  it('stores and replays a successful result without executing the operation twice', async () => {
    const operation = jest.fn(() => Promise.resolve(success(VALUE)));

    const first = await useCase.execute(KEY, OPERATION, BODY, operation);
    const replay = await useCase.execute(KEY, OPERATION, BODY, operation);
    const [records] = await relationalDataService.idempotencyKey.findAll({limit: 10});

    expect(first).toEqual(success(VALUE));
    expect(replay).toEqual(first);
    expect(operation).toHaveBeenCalledTimes(1);
    expect(records).toEqual([
      expect.objectContaining({
        key: KEY,
        operationId: OPERATION,
        response: VALUE,
        responseVersion: 1,
        createdAt: mockNow,
        expiresAt: new Date(mockNow.getTime() + 5 * 60 * 1000),
      }),
    ]);
  });

  it('does not store an expected failure so a retry can execute again', async () => {
    const failure = {name: 'EventNotFoundError'};
    const operation = jest.fn(() => Promise.resolve(error<typeof VALUE, typeof failure>(failure)));

    await expect(useCase.execute(KEY, OPERATION, BODY, operation)).resolves.toEqual(error(failure));
    await expect(useCase.execute(KEY, OPERATION, BODY, operation)).resolves.toEqual(error(failure));
    const [records] = await relationalDataService.idempotencyKey.findAll({limit: 10});

    expect(operation).toHaveBeenCalledTimes(2);
    expect(records).toEqual([]);
  });

  it('returns a hash mismatch as an expected result', async () => {
    await useCase.execute(KEY, OPERATION, BODY, () => Promise.resolve(success(VALUE)));

    const result = await useCase.execute(KEY, OPERATION, {amount: 999}, () =>
      Promise.resolve(success({id: 'duplicate'})),
    );

    expect(isError(result)).toBe(true);
    if (isError(result)) {
      expect(result.error).toBeInstanceOf(IdempotencyHashMismatchError);
    }
  });

  it('uses canonical object key ordering for request fingerprints', async () => {
    const operation = jest.fn(() => Promise.resolve(success(VALUE)));

    await useCase.execute(KEY, OPERATION, {amount: 100, metadata: {b: 2, a: 1}}, operation);
    const replay = await useCase.execute(KEY, OPERATION, {metadata: {a: 1, b: 2}, amount: 100}, operation);

    expect(replay).toEqual(success(VALUE));
    expect(operation).toHaveBeenCalledTimes(1);
  });

  it('restores Date instances when replaying a response', async () => {
    const createdAt = new Date('2026-01-02T03:04:05.000Z');

    await useCase.execute(KEY, OPERATION, BODY, () => Promise.resolve(success({createdAt})));
    const replay = await useCase.execute(KEY, OPERATION, BODY, () =>
      Promise.resolve(success({createdAt: new Date(0)})),
    );

    expect(replay).toEqual(success({createdAt}));
    if (!isError(replay)) {
      expect(replay.value.createdAt).toBeInstanceOf(Date);
    }
  });

  it('replays a versioned record written by the previous backend release', async () => {
    const requestHash = createHash('sha256')
      .update(JSON.stringify({body: BODY, operationId: OPERATION}))
      .digest('hex');
    await relationalDataService.dataSource.query(
      `INSERT INTO idempotency_keys
        (key, url, request_hash, response, status_code, operation_id, request_hash_v2, response_v2,
         response_version, created_at, expires_at)
       VALUES ($1, $2, $3, $4, 200, $5, $6, $7, 1, $8, $9)`,
      [
        KEY,
        '/old/url',
        'old-hash',
        success(VALUE),
        OPERATION,
        requestHash,
        VALUE,
        mockNow,
        new Date(mockNow.getTime() + 60_000),
      ],
    );
    const operation = jest.fn(() => Promise.resolve(success({id: 'duplicate'})));

    const replay = await useCase.execute(KEY, OPERATION, BODY, operation);

    expect(replay).toEqual(success(VALUE));
    expect(operation).not.toHaveBeenCalled();
  });

  it('stores only the versioned response without legacy transport data', async () => {
    await useCase.execute(KEY, OPERATION, BODY, () => Promise.resolve(success(VALUE)));

    const records = await relationalDataService.dataSource.query<
      {
        url: string | null;
        request_hash: string | null;
        response: object | null;
        status_code: number | null;
      }[]
    >('SELECT url, request_hash, response, status_code FROM idempotency_keys WHERE key = $1', [KEY]);

    expect(records).toEqual([{url: null, request_hash: null, response: null, status_code: null}]);
  });

  it('replaces a legacy-only record whose effective five-minute TTL has elapsed', async () => {
    await relationalDataService.dataSource.query(
      `INSERT INTO idempotency_keys
        (key, url, request_hash, response, status_code, created_at, expires_at)
       VALUES ($1, '/old/url', 'old-hash', $2, 200, $3, $4)`,
      [
        KEY,
        success({id: 'old-expense'}),
        new Date(mockNow.getTime() - 5 * 60 * 1000),
        new Date(mockNow.getTime() + 24 * 60 * 60 * 1000),
      ],
    );

    const result = await useCase.execute(KEY, OPERATION, BODY, () => Promise.resolve(success(VALUE)));
    const [records] = await relationalDataService.idempotencyKey.findAll({limit: 10});

    expect(result).toEqual(success(VALUE));
    expect(records).toEqual([expect.objectContaining({key: KEY, operationId: OPERATION, response: VALUE})]);
  });

  it('executes again after the five-minute TTL and replaces the expired record', async () => {
    await useCase.execute(KEY, OPERATION, BODY, () => Promise.resolve(success(VALUE)));
    jest.setSystemTime(new Date(mockNow.getTime() + 5 * 60 * 1000));
    const replacement = {id: 'expense-2', amount: 100};
    const operation = jest.fn(() => Promise.resolve(success(replacement)));

    const result = await useCase.execute(KEY, OPERATION, BODY, operation);

    expect(result).toEqual(success(replacement));
    expect(operation).toHaveBeenCalledTimes(1);
  });

  it('rejects a concurrent execution with the same key before running its operation', async () => {
    let releaseFirst!: () => void;
    let markFirstStarted!: () => void;
    const firstStarted = new Promise<void>((resolve) => {
      markFirstStarted = resolve;
    });
    const releaseFirstExecution = new Promise<void>((resolve) => {
      releaseFirst = resolve;
    });
    const firstOperation = jest.fn(async () => {
      markFirstStarted();
      await releaseFirstExecution;
      return success(VALUE);
    });
    const secondOperation = jest.fn(() => Promise.resolve(success({id: 'duplicate'})));

    const firstExecution = useCase.execute(KEY, OPERATION, BODY, firstOperation);
    await firstStarted;
    const secondResult = await useCase.execute(KEY, OPERATION, BODY, secondOperation);
    releaseFirst();
    await firstExecution;

    expect(isError(secondResult)).toBe(true);
    if (isError(secondResult)) {
      expect(secondResult.error).toBeInstanceOf(IdempotencyRequestInProgressError);
    }
    expect(firstOperation).toHaveBeenCalledTimes(1);
    expect(secondOperation).not.toHaveBeenCalled();
  });

  it('releases the lock and stores nothing when the operation throws', async () => {
    await expect(useCase.execute(KEY, OPERATION, BODY, () => Promise.reject(new Error('boom')))).rejects.toThrow(
      'boom',
    );

    await expect(useCase.execute(KEY, OPERATION, BODY, () => Promise.resolve(success(VALUE)))).resolves.toEqual(
      success(VALUE),
    );
  });
});
