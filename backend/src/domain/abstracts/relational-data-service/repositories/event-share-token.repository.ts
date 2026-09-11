import {IQueryDetails, ITransaction} from '#domain/abstracts/relational-data-service/types';
import {IEventShareToken} from '#domain/entities/event-share-token.entity';

export abstract class EventShareTokenRepositoryAbstract {
  abstract findByToken: (
    token: string,
    trx?: ITransaction,
  ) => Promise<[result: IEventShareToken | null, queryDetails: IQueryDetails]>;
  abstract findOneActiveByEventId: (
    eventId: string,
    trx?: ITransaction,
  ) => Promise<[result: IEventShareToken | null, queryDetails: IQueryDetails]>;
  abstract findAll: (
    input: {limit: number},
    trx?: ITransaction,
  ) => Promise<[result: IEventShareToken[], queryDetails: IQueryDetails]>;
  abstract insert: (
    token: IEventShareToken | IEventShareToken[],
    trx?: ITransaction,
  ) => Promise<[result: undefined, queryDetails: IQueryDetails]>;
  abstract deleteByToken: (
    token: string,
    trx?: ITransaction,
  ) => Promise<[result: undefined, queryDetails: IQueryDetails]>;
}
