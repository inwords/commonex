import {IQueryDetails, ITransaction, ITransactionWithLock} from '#domain/abstracts/relational-data-service/types';
import {IEvent} from '#domain/entities/event.entity';

export abstract class EventRepositoryAbstract {
  abstract findById: (
    id: IEvent['id'],
    trx?: ITransactionWithLock,
  ) => Promise<[result: IEvent | null, queryDetails: IQueryDetails]>;
  abstract findAll: (
    input: {limit: number},
    trx?: ITransaction,
  ) => Promise<[result: IEvent[], queryDetails: IQueryDetails]>;
  abstract insert: (
    event: IEvent | IEvent[],
    trx?: ITransaction,
  ) => Promise<[result: undefined, queryDetails: IQueryDetails]>;
  abstract update: (
    id: IEvent['id'],
    data: Partial<Omit<IEvent, 'id'>>,
    trx?: ITransaction,
  ) => Promise<[result: undefined, queryDetails: IQueryDetails]>;
}
