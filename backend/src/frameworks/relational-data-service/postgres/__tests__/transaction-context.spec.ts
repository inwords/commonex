import {DataSource, EntityManager, type QueryRunner} from 'typeorm';

import {ITransactionWithLock} from '#domain/abstracts/relational-data-service/types';

import {createTransactionHandle, invalidateTransactionHandle, resolveTransactionManager} from '../transaction-context';

const createDataSource = (): DataSource => new DataSource({type: 'postgres', database: 'transaction-context-test'});

const createManager = (dataSource: DataSource, queryRunner: QueryRunner): EntityManager =>
  new EntityManager(dataSource, queryRunner);

describe('transaction context', () => {
  it('allows global repository selection only when the transaction argument is absent', () => {
    expect(resolveTransactionManager(createDataSource())).toBeUndefined();
  });

  it('resolves a framework handle and preserves its token when spread', () => {
    const dataSource = createDataSource();
    const queryRunner = {isReleased: false, isTransactionActive: true} as QueryRunner;
    const manager = createManager(dataSource, queryRunner);
    const trx = createTransactionHandle(dataSource, manager);
    const lockedTrx: ITransactionWithLock = {...trx, lock: 'pessimistic_write', onLocked: 'nowait'};

    expect(Object.isFrozen(trx.ctx)).toBe(true);
    expect(resolveTransactionManager(dataSource, trx)).toBe(manager);
    expect(resolveTransactionManager(dataSource, lockedTrx)).toBe(manager);
  });

  it.each([
    ['a missing context', {}],
    ['an undefined context', {ctx: undefined}],
    ['a null context', {ctx: null}],
    ['a plain object context', {ctx: {}}],
    ['a primitive context', {ctx: 'manager'}],
    ['a null transaction', null],
    ['a primitive transaction', 'transaction'],
  ])('rejects %s before returning a manager', (_name, trx) => {
    expect(() => resolveTransactionManager(createDataSource(), trx as {ctx: unknown})).toThrow(TypeError);
  });

  it('rejects the global manager because it was not issued as a transaction handle', () => {
    const dataSource = createDataSource();

    expect(() => resolveTransactionManager(dataSource, {ctx: dataSource.manager})).toThrow(TypeError);
  });

  it('rejects a raw transaction manager that was not issued as a framework handle', () => {
    const dataSource = createDataSource();
    const manager = createManager(dataSource, {isReleased: false, isTransactionActive: true} as QueryRunner);

    expect(() => resolveTransactionManager(dataSource, {ctx: manager})).toThrow(TypeError);
  });

  it('rejects a handle owned by another data source', () => {
    const owner = createDataSource();
    const foreign = createDataSource();
    const manager = createManager(owner, {isReleased: false, isTransactionActive: true} as QueryRunner);
    const trx = createTransactionHandle(owner, manager);

    expect(() => resolveTransactionManager(foreign, trx)).toThrow(TypeError);
  });

  it('rejects a handle after its transaction callback completes', () => {
    const dataSource = createDataSource();
    const manager = createManager(dataSource, {isReleased: false, isTransactionActive: true} as QueryRunner);
    const trx = createTransactionHandle(dataSource, manager);
    invalidateTransactionHandle(trx);

    expect(() => resolveTransactionManager(dataSource, trx)).toThrow(TypeError);
  });

  it('rejects a registered manager that has no query runner', () => {
    const dataSource = createDataSource();
    const manager = createManager(dataSource, undefined as never);
    const trx = createTransactionHandle(dataSource, manager);

    expect(() => resolveTransactionManager(dataSource, trx)).toThrow(TypeError);
  });

  it.each([
    ['released', {isReleased: true, isTransactionActive: true}],
    ['inactive', {isReleased: false, isTransactionActive: false}],
  ])('rejects a registered handle with a %s query runner', (_name, state) => {
    const dataSource = createDataSource();
    const manager = createManager(dataSource, state as QueryRunner);
    const trx = createTransactionHandle(dataSource, manager);

    expect(() => resolveTransactionManager(dataSource, trx)).toThrow(TypeError);
  });
});
