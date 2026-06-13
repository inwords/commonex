import {ExpenseBase} from '@/5-entities/expense/types/types';

type CorrectionStatusContent = {
  editedOn: (date: string) => string;
  revertedOn: (date: string) => string;
};

type CorrectionOperation = Pick<ExpenseBase, 'createdAt' | 'revertsExpenseId' | 'replacesExpenseId'>;

const formatCorrectionDate = (createdAt: string): string => {
  return new Intl.DateTimeFormat(undefined, {dateStyle: 'long'}).format(new Date(createdAt));
};

export const buildCorrectionStatusByExpenseId = (
  operations: CorrectionOperation[],
  content: CorrectionStatusContent,
): Map<string, string> => {
  const statuses = new Map<string, string>();

  for (const operation of operations) {
    const formattedDate = formatCorrectionDate(operation.createdAt);

    if (operation.revertsExpenseId != null) {
      statuses.set(operation.revertsExpenseId, content.revertedOn(formattedDate));
    }

    if (operation.replacesExpenseId != null) {
      statuses.set(operation.replacesExpenseId, content.editedOn(formattedDate));
    }
  }

  return statuses;
};

export const getExpenseCorrectionStatus = (
  expense: Pick<ExpenseBase, 'id'>,
  operations: CorrectionOperation[],
  content: CorrectionStatusContent,
): string | null => {
  const correction = operations.find((operation) => {
    return operation.revertsExpenseId === expense.id || operation.replacesExpenseId === expense.id;
  });

  if (correction == null) {
    return null;
  }

  const formattedDate = formatCorrectionDate(correction.createdAt);

  if (correction.replacesExpenseId === expense.id) {
    return content.editedOn(formattedDate);
  }

  return content.revertedOn(formattedDate);
};

export const isExpenseCorrectionOperation = (
  operation: Pick<ExpenseBase, 'revertsExpenseId' | 'replacesExpenseId'>,
): boolean => {
  return operation.revertsExpenseId != null || operation.replacesExpenseId != null;
};

export const filterActiveExpenses = <T extends Pick<ExpenseBase, 'id' | 'replacesExpenseId'>>(
  expenses: T[],
): T[] => {
  const replacedExpenseIds = new Set(
    expenses.map((expense) => expense.replacesExpenseId).filter((id): id is string => id != null),
  );

  return expenses.filter((expense) => !replacedExpenseIds.has(expense.id));
};
