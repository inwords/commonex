import {IIdempotencyKey} from '#domain/entities/idempotency-key.entity';
import {IQueryDetails, ITransaction} from '#domain/abstracts/relational-data-service/types';
import {FindOptionsWhere} from 'typeorm';

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

  abstract delete: (
    criteria: FindOptionsWhere<IIdempotencyKey>,
    trx?: ITransaction,
  ) => Promise<[result: undefined, queryDetails: IQueryDetails]>;
}
