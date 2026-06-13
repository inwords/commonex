import {IExpense} from '#domain/entities/expense.entity';
import {Result, error, success} from '#packages/result';
import {
  ExpenseAlreadyRevertedError,
  ExpenseCorrectionConflictError,
  ExpenseReferenceNotFoundError,
} from '#domain/errors/errors';

export type ExpenseCorrectionLinksInput = Pick<IExpense, 'revertsExpenseId' | 'replacesExpenseId'>;

export const isExpenseCorrection = (expense: ExpenseCorrectionLinksInput): boolean => {
  return expense.revertsExpenseId != null || expense.replacesExpenseId != null;
};

export const validateExpenseCorrectionLinks = (
  input: ExpenseCorrectionLinksInput,
  referencedExpense: IExpense | undefined,
  existingCorrection: IExpense | undefined,
): Result<
  true,
  ExpenseAlreadyRevertedError | ExpenseCorrectionConflictError | ExpenseReferenceNotFoundError
> => {
  const {revertsExpenseId, replacesExpenseId} = input;

  if (revertsExpenseId != null && replacesExpenseId != null) {
    return error(new ExpenseCorrectionConflictError());
  }

  const referencedExpenseId = revertsExpenseId ?? replacesExpenseId;

  if (referencedExpenseId == null) {
    return success(true);
  }

  if (referencedExpense === undefined) {
    return error(new ExpenseReferenceNotFoundError());
  }

  if (existingCorrection !== undefined) {
    if (revertsExpenseId != null && existingCorrection.revertsExpenseId === revertsExpenseId) {
      return error(new ExpenseAlreadyRevertedError());
    }
    return error(new ExpenseCorrectionConflictError());
  }

  return success(true);
};
