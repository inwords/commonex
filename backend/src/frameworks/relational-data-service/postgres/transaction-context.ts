import {DataSource, EntityManager} from 'typeorm';

import {ITransaction} from '#domain/abstracts/relational-data-service/types';

interface TransactionContext {
  dataSource: DataSource;
  manager: EntityManager;
}

const transactionContexts = new WeakMap<object, TransactionContext>();

export const createTransactionHandle = (dataSource: DataSource, manager: EntityManager): ITransaction => {
  const token = Object.freeze({});
  transactionContexts.set(token, {dataSource, manager});

  return Object.freeze({ctx: token});
};

export const invalidateTransactionHandle = (trx: ITransaction): void => {
  if (typeof trx.ctx === 'object' && trx.ctx !== null) {
    transactionContexts.delete(trx.ctx);
  }
};

export const resolveTransactionManager = (dataSource: DataSource, trx?: unknown): EntityManager | undefined => {
  if (trx === undefined) {
    return undefined;
  }

  if (typeof trx !== 'object' || trx === null || !('ctx' in trx)) {
    throw new TypeError('Invalid transaction handle');
  }

  const token = trx.ctx;
  if (typeof token !== 'object' || token === null) {
    throw new TypeError('Invalid transaction handle');
  }

  const context = transactionContexts.get(token);
  if (context === undefined) {
    throw new TypeError('Invalid transaction handle');
  }

  if (
    context.dataSource !== dataSource ||
    !(context.manager instanceof EntityManager) ||
    context.manager.dataSource !== dataSource ||
    context.manager.queryRunner == null ||
    context.manager.queryRunner.isReleased ||
    !context.manager.queryRunner.isTransactionActive
  ) {
    throw new TypeError('Invalid transaction handle');
  }

  return context.manager;
};

export const requireTransactionManager = (dataSource: DataSource, trx: ITransaction): EntityManager => {
  const manager = resolveTransactionManager(dataSource, trx);
  if (manager == null) {
    throw new TypeError('An active transaction handle is required');
  }

  return manager;
};
