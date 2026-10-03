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
const LEGACY_OPERATION = '/v2/user/event/event-1/expense';

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

    await expect(useCase.execute(undefined, OPERATION, undefined, BODY, operation)).resolves.toEqual(success(VALUE));
    expect(operation).toHaveBeenCalledTimes(1);
  });

  it('stores and replays a successful result without executing the operation twice', async () => {
    const operation = jest.fn(() => Promise.resolve(success(VALUE)));

    const first = await useCase.execute(KEY, OPERATION, LEGACY_OPERATION, BODY, operation);
    const replay = await useCase.execute(KEY, OPERATION, LEGACY_OPERATION, BODY, operation);
    const [records] = await relationalDataService.idempotencyKey.findAll({limit: 10});

    expect(first).toEqual(success(VALUE));
    expect(replay).toEqual(first);
    expect(operation).toHaveBeenCalledTimes(1);
    expect(records).toEqual([
      expect.objectContaining({
        key: KEY,
        url: LEGACY_OPERATION,
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

    await expect(useCase.execute(KEY, OPERATION, LEGACY_OPERATION, BODY, operation)).resolves.toEqual(error(failure));
    await expect(useCase.execute(KEY, OPERATION, LEGACY_OPERATION, BODY, operation)).resolves.toEqual(error(failure));
    const [records] = await relationalDataService.idempotencyKey.findAll({limit: 10});

    expect(operation).toHaveBeenCalledTimes(2);
    expect(records).toEqual([]);
  });

  it('returns a hash mismatch as an expected result', async () => {
    await useCase.execute(KEY, OPERATION, LEGACY_OPERATION, BODY, () => Promise.resolve(success(VALUE)));

    const result = await useCase.execute(KEY, OPERATION, LEGACY_OPERATION, {amount: 999}, () =>
      Promise.resolve(success({id: 'duplicate'})),
    );

    expect(isError(result)).toBe(true);
    if (isError(result)) {
      expect(result.error).toBeInstanceOf(IdempotencyHashMismatchError);
    }
  });

  it('uses canonical object key ordering for request fingerprints', async () => {
    const operation = jest.fn(() => Promise.resolve(success(VALUE)));

    await useCase.execute(KEY, OPERATION, LEGACY_OPERATION, {amount: 100, metadata: {b: 2, a: 1}}, operation);
    const replay = await useCase.execute(
      KEY,
      OPERATION,
      LEGACY_OPERATION,
      {metadata: {a: 1, b: 2}, amount: 100},
      operation,
    );

    expect(replay).toEqual(success(VALUE));
    expect(operation).toHaveBeenCalledTimes(1);
  });

  it('restores Date instances when replaying a response', async () => {
    const createdAt = new Date('2026-01-02T03:04:05.000Z');

    await useCase.execute(KEY, OPERATION, LEGACY_OPERATION, BODY, () => Promise.resolve(success({createdAt})));
    const replay = await useCase.execute(KEY, OPERATION, LEGACY_OPERATION, BODY, () =>
      Promise.resolve(success({createdAt: new Date(0)})),
    );

    expect(replay).toEqual(success({createdAt}));
    if (!isError(replay)) {
      expect(replay.value.createdAt).toBeInstanceOf(Date);
    }
  });

  it('replays a legacy record during the expand/contract window', async () => {
    const legacyValue = {id: 'legacy-expense', createdAt: '2026-01-01T00:00:00.000Z'};
    const legacyRequestHash = createHash('sha256')
      .update(JSON.stringify({url: LEGACY_OPERATION, body: BODY}))
      .digest('hex');
    await relationalDataService.idempotencyKey.insert({
      key: KEY,
      url: LEGACY_OPERATION,
      legacyRequestHash,
      legacyResponse: success(legacyValue),
      statusCode: 200,
      operationId: null,
      requestHash: null,
      response: null,
      responseVersion: null,
      createdAt: mockNow,
      expiresAt: new Date(mockNow.getTime() + 60_000),
    });
    const operation = jest.fn(() => Promise.resolve(success(VALUE)));

    const replay = await useCase.execute(KEY, OPERATION, LEGACY_OPERATION, BODY, operation);

    expect(replay).toEqual(success(legacyValue));
    expect(operation).not.toHaveBeenCalled();
  });

  it('executes again after the five-minute TTL and replaces the expired record', async () => {
    await useCase.execute(KEY, OPERATION, LEGACY_OPERATION, BODY, () => Promise.resolve(success(VALUE)));
    jest.setSystemTime(new Date(mockNow.getTime() + 5 * 60 * 1000));
    const replacement = {id: 'expense-2', amount: 100};
    const operation = jest.fn(() => Promise.resolve(success(replacement)));

    const result = await useCase.execute(KEY, OPERATION, LEGACY_OPERATION, BODY, operation);

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

    const firstExecution = useCase.execute(KEY, OPERATION, LEGACY_OPERATION, BODY, firstOperation);
    await firstStarted;
    const secondResult = await useCase.execute(KEY, OPERATION, LEGACY_OPERATION, BODY, secondOperation);
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
    await expect(
      useCase.execute(KEY, OPERATION, LEGACY_OPERATION, BODY, () => Promise.reject(new Error('boom'))),
    ).rejects.toThrow('boom');

    await expect(
      useCase.execute(KEY, OPERATION, LEGACY_OPERATION, BODY, () => Promise.resolve(success(VALUE))),
    ).resolves.toEqual(success(VALUE));
  });
});
