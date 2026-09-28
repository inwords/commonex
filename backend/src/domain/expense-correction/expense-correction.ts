import {Result, error, success} from '#packages/result';

import {ExpenseType, IExpense} from '#domain/entities/expense.entity';
import {
  ExpenseAlreadyRevertedError,
  ExpenseCorrectionConflictError,
  ExpenseReferenceNotFoundError,
} from '#domain/errors/errors';
import {ExpenseValueObject} from '#domain/value-objects/expense.value-object';

export type ExpenseCorrectionLinksInput = Pick<IExpense, 'eventId' | 'revertsExpenseId' | 'replacesExpenseId'>;

export const createExpenseReversal = (
  referencedExpense: IExpense,
  metadata: Pick<IExpense, 'description'> & Partial<Pick<IExpense, 'createdAt'>>,
): IExpense =>
  new ExpenseValueObject({
    eventId: referencedExpense.eventId,
    currencyId: referencedExpense.currencyId,
    userWhoPaidId: referencedExpense.userWhoPaidId,
    expenseType: referencedExpense.expenseType === ExpenseType.Expense ? ExpenseType.Refund : ExpenseType.Expense,
    isCustomRate: referencedExpense.isCustomRate,
    splitInformation: referencedExpense.splitInformation.map((split) => ({
      userId: split.userId,
      amount: -split.amount,
      exchangedAmount: -split.exchangedAmount,
    })),
    revertsExpenseId: referencedExpense.id,
    description: metadata.description,
    ...(metadata.createdAt !== undefined ? {createdAt: metadata.createdAt} : {}),
  }).value;

export const validateExpenseCorrectionLinks = (
  input: ExpenseCorrectionLinksInput,
  referencedExpense: IExpense | null,
  existingCorrection: IExpense | null,
): Result<IExpense, ExpenseAlreadyRevertedError | ExpenseCorrectionConflictError | ExpenseReferenceNotFoundError> => {
  const {revertsExpenseId, replacesExpenseId} = input;

  if (revertsExpenseId != null && replacesExpenseId != null) {
    return error(new ExpenseCorrectionConflictError());
  }

  if (referencedExpense === null) {
    return error(new ExpenseReferenceNotFoundError());
  }

  if (referencedExpense.eventId !== input.eventId) {
    return error(new ExpenseReferenceNotFoundError());
  }

  if (existingCorrection !== null) {
    if (revertsExpenseId != null && existingCorrection.revertsExpenseId === revertsExpenseId) {
      return error(new ExpenseAlreadyRevertedError());
    }
    return error(new ExpenseCorrectionConflictError());
  }

  return success(referencedExpense);
};
