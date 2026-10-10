import {DataSource, EntityManager, type QueryRunner} from 'typeorm';

import {ITransaction, ITransactionWithLock} from '#domain/abstracts/relational-data-service/types';

import {RelationalDataService} from '../relational-data-service';
import {createTransactionHandle, resolveTransactionManager} from '../transaction-context';

const createDataSource = (): DataSource => new DataSource({type: 'postgres', database: 'transaction-context-test'});

const createService = (dataSource: DataSource): RelationalDataService => {
  const service = Object.create(RelationalDataService.prototype) as RelationalDataService;
  Object.defineProperty(service, 'dataSource', {value: dataSource});
  return service;
};

const createManager = (dataSource: DataSource): EntityManager =>
  new EntityManager(dataSource, {isReleased: false, isTransactionActive: true} as QueryRunner);

describe('RelationalDataService transaction', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it.each(['successful', 'throwing'])('invalidates the handle after a %s callback', async (outcome) => {
    const dataSource = createDataSource();
    const service = createService(dataSource);
    const manager = createManager(dataSource);
    let trx: ITransactionWithLock | undefined;
    const runDataSourceTransaction = jest.fn(async (run: (manager: EntityManager) => Promise<unknown>) => run(manager));
    Object.defineProperty(dataSource, 'transaction', {value: runDataSourceTransaction});

    const callback = (handle: ITransaction): Promise<string> => {
      const handleWithLock: ITransactionWithLock = {...handle, lock: 'pessimistic_write'};
      trx = handleWithLock;
      if (outcome === 'throwing') {
        return Promise.reject(new Error('callback failed'));
      }
      return Promise.resolve('complete');
    };

    if (outcome === 'throwing') {
      await expect(service.transaction(callback)).rejects.toThrow('callback failed');
    } else {
      await expect(service.transaction(callback)).resolves.toBe('complete');
    }

    expect(trx).toBeDefined();
    expect(() => resolveTransactionManager(dataSource, trx)).toThrow(TypeError);
  });

  it('keeps the transaction handle opaque across both transaction overloads', async () => {
    const dataSource = createDataSource();
    const service = createService(dataSource);
    const manager = createManager(dataSource);
    const calls: unknown[][] = [];
    Object.defineProperty(dataSource, 'transaction', {
      value: (...args: unknown[]) => {
        calls.push(args);
        const callback = args.at(-1) as (manager: EntityManager) => Promise<unknown>;
        return callback(manager);
      },
    });

    await service.transaction((trx) => {
      expect(trx).toEqual({ctx: expect.any(Object)});
      expect(Object.isFrozen(trx)).toBe(true);
      expect(resolveTransactionManager(dataSource, trx)).toBe(manager);
      return Promise.resolve();
    });
    await service.transaction('SERIALIZABLE', (trx) => {
      expect(resolveTransactionManager(dataSource, trx)).toBe(manager);
      return Promise.resolve();
    });

    expect(calls[0]).toHaveLength(1);
    expect(calls[1]?.[0]).toBe('SERIALIZABLE');
  });

  it('requires an opaque transaction handle before attempting the advisory lock query', async () => {
    const dataSource = createDataSource();
    const service = createService(dataSource);
    const query = jest.spyOn(EntityManager.prototype, 'query');
    const trx = {ctx: {}} as ITransaction;

    await expect(service.tryAcquireTransactionLock('test-key', trx)).rejects.toBeInstanceOf(TypeError);
    expect(query).not.toHaveBeenCalled();
  });

  it('rejects a missing required lock handle before attempting SQL', async () => {
    const dataSource = createDataSource();
    const service = createService(dataSource);
    const query = jest.spyOn(EntityManager.prototype, 'query');

    await expect(service.tryAcquireTransactionLock('test-key', undefined as never)).rejects.toBeInstanceOf(TypeError);
    expect(query).not.toHaveBeenCalled();
  });

  it('keeps the advisory lock query and parameter shape unchanged', async () => {
    const dataSource = createDataSource();
    const service = createService(dataSource);
    const manager = createManager(dataSource);
    const query = jest.spyOn(manager, 'query').mockResolvedValue([{acquired: true}]);
    const trx = createTransactionHandle(dataSource, manager);

    await expect(service.tryAcquireTransactionLock('commonex:cron:test', trx)).resolves.toBe(true);
    expect(query).toHaveBeenCalledWith('SELECT pg_try_advisory_xact_lock(hashtextextended($1, 0)) AS acquired', [
      'commonex:cron:test',
    ]);
  });
});
