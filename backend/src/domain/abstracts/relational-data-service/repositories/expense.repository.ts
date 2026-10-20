import {IQueryDetails, ITransaction, ITransactionWithLock} from '#domain/abstracts/relational-data-service/types';
import {IExpense} from '#domain/entities/expense.entity';

export abstract class ExpenseRepositoryAbstract {
  abstract findByEventId: (
    eventId: IExpense['eventId'],
    trx?: ITransactionWithLock,
  ) => Promise<[result: IExpense[], queryDetails: IQueryDetails]>;
  abstract findLatestById: (
    eventId: IExpense['eventId'],
    id: IExpense['id'],
    trx?: ITransactionWithLock,
  ) => Promise<[result: IExpense | null, queryDetails: IQueryDetails]>;
  abstract findHistoryById: (
    eventId: IExpense['eventId'],
    id: IExpense['id'],
    trx?: ITransaction,
  ) => Promise<[result: IExpense[], queryDetails: IQueryDetails]>;
  abstract findAll: (
    input: {limit: number},
    trx?: ITransaction,
  ) => Promise<[result: IExpense[], queryDetails: IQueryDetails]>;
  abstract insert: (
    expense: IExpense | IExpense[],
    trx?: ITransaction,
  ) => Promise<[result: undefined, queryDetails: IQueryDetails]>;
}
