import {FindOptionsWhere} from 'typeorm';

import {IQueryDetails, ITransaction} from '#domain/abstracts/relational-data-service/types';
import {IIdempotencyKey} from '#domain/entities/idempotency-key.entity';

export abstract class IdempotencyKeyRepositoryAbstract {
  abstract findByKey: (
    key: IIdempotencyKey['key'],
    trx?: ITransaction,
  ) => Promise<[result: IIdempotencyKey | null, queryDetails: IQueryDetails]>;

  abstract findAll: (
    input: {limit: number},
    trx?: ITransaction,
  ) => Promise<[result: IIdempotencyKey[], queryDetails: IQueryDetails]>;

  abstract insert: (
    input: IIdempotencyKey,
    trx?: ITransaction,
  ) => Promise<[result: undefined, queryDetails: IQueryDetails]>;

  abstract tryAcquireLock: (
    key: IIdempotencyKey['key'],
    trx: ITransaction,
  ) => Promise<[acquired: boolean, queryDetails: IQueryDetails]>;

  abstract delete: (
    criteria: FindOptionsWhere<IIdempotencyKey>,
    trx?: ITransaction,
  ) => Promise<[result: undefined, queryDetails: IQueryDetails]>;

  abstract deleteExpiredBatch: (
    input: {expiresBefore: Date; limit: number},
    trx?: ITransaction,
  ) => Promise<[deletedCount: number, queryDetails: IQueryDetails]>;
}
