import {ExpenseAlreadyRevertedError, ExpenseCorrectionConflictError, ExpenseReferenceNotFoundError} from '#domain/errors/errors';
import {validateExpenseCorrectionLinks} from '#domain/expense-correction/expense-correction';
import {ExpenseType, IExpense} from '#domain/entities/expense.entity';
import {error, isSuccess} from '#packages/result';

describe('validateExpenseCorrectionLinks', () => {
  const referencedExpense: IExpense = {
    id: 'expense-1',
    description: 'Dinner',
    userWhoPaidId: 'user-1',
    currencyId: 'currency-usd',
    eventId: 'event-1',
    expenseType: ExpenseType.Expense,
    splitInformation: [],
    isCustomRate: false,
    revertsExpenseId: null,
    replacesExpenseId: null,
    createdAt: new Date('2026-01-01T00:00:00Z'),
    updatedAt: new Date('2026-01-01T00:00:00Z'),
  };

  it('returns success when no correction links are provided', () => {
    const result = validateExpenseCorrectionLinks(
      {
        revertsExpenseId: null,
        replacesExpenseId: null,
      },
      undefined,
      undefined,
    );

    expect(isSuccess(result)).toBe(true);
  });

  it('returns reference not found when referenced expense is missing', () => {
    const result = validateExpenseCorrectionLinks(
      {
        revertsExpenseId: 'expense-1',
        replacesExpenseId: null,
      },
      undefined,
      undefined,
    );

    expect(result).toEqual(error(new ExpenseReferenceNotFoundError()));
  });

  it('returns conflict when both correction links are provided', () => {
    const result = validateExpenseCorrectionLinks(
      {
        revertsExpenseId: 'expense-1',
        replacesExpenseId: 'expense-1',
      },
      referencedExpense,
      undefined,
    );

    expect(result).toEqual(error(new ExpenseCorrectionConflictError()));
  });

  it('returns already reverted when duplicate revert is submitted', () => {
    const result = validateExpenseCorrectionLinks(
      {
        revertsExpenseId: 'expense-1',
        replacesExpenseId: null,
      },
      referencedExpense,
      {
        ...referencedExpense,
        id: 'revert-1',
        revertsExpenseId: 'expense-1',
        replacesExpenseId: null,
      },
    );

    expect(result).toEqual(error(new ExpenseAlreadyRevertedError()));
  });
});
