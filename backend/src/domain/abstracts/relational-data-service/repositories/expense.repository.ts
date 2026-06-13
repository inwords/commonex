import {IQueryDetails, ITransaction, ITransactionWithLock} from '#domain/abstracts/relational-data-service/types';
import {IExpense} from '#domain/entities/expense.entity';

export abstract class ExpenseRepositoryAbstract {
  abstract findById: (
    expenseId: IExpense['id'],
    trx?: ITransaction,
  ) => Promise<[result: IExpense | undefined, queryDetails: IQueryDetails]>;
  abstract findCorrectionForReferencedExpense: (
    eventId: IExpense['eventId'],
    referencedExpenseId: IExpense['id'],
    trx?: ITransaction,
  ) => Promise<[result: IExpense | undefined, queryDetails: IQueryDetails]>;
  abstract findByEventId: (
    eventId: IExpense['eventId'],
    trx?: ITransactionWithLock,
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
