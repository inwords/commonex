export interface ITransaction {
  ctx?: unknown;
}

export interface ITransactionWithLock {
  ctx?: unknown;
  lock?: 'pessimistic_read' | 'pessimistic_write' | undefined;
  onLocked?: 'nowait' | 'skip_locked' | undefined;
}

export interface IQueryDetails {
  queryString: unknown;
  queryParameters: unknown;
}

export interface IRelationalDataService {
  initialize: () => Promise<void>;
  // The domain keeps the ORM manager and isolation level opaque; the generics are placeholders the framework layer fills in.
  /* eslint-disable @typescript-eslint/no-unnecessary-type-parameters */
  transaction: (<T, TEntityManager>(runInTransaction: (entityManager: TEntityManager) => Promise<T>) => Promise<T>) &
    (<T, TEntityManager, TIsolationLevel>(
      isolationLevel: TIsolationLevel,
      runInTransaction: (entityManager: TEntityManager) => Promise<T>,
    ) => Promise<T>);
  /* eslint-enable @typescript-eslint/no-unnecessary-type-parameters */
  destroy: () => Promise<void>;
  healthCheck: () => Promise<void>;
}
