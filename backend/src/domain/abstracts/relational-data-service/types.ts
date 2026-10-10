export interface ITransaction {
  readonly ctx: unknown;
}

export interface ITransactionWithLock extends ITransaction {
  lock?: 'pessimistic_read' | 'pessimistic_write' | undefined;
  onLocked?: 'nowait' | 'skip_locked' | undefined;
}

export type ITransactionIsolationLevel = 'READ UNCOMMITTED' | 'READ COMMITTED' | 'REPEATABLE READ' | 'SERIALIZABLE';

export interface IQueryDetails {
  queryString: unknown;
  queryParameters: unknown;
}

export interface IRelationalDataService {
  initialize: () => Promise<void>;
  transaction: {
    <T>(runInTransaction: (trx: ITransaction) => Promise<T>): Promise<T>;
    <T>(isolationLevel: ITransactionIsolationLevel, runInTransaction: (trx: ITransaction) => Promise<T>): Promise<T>;
  };
  tryAcquireTransactionLock: (key: string, trx: ITransaction) => Promise<boolean>;
  destroy: () => Promise<void>;
  healthCheck: () => Promise<void>;
}
