import {error, success} from '#packages/result';

import {ExpenseType, IExpense} from '#domain/entities/expense.entity';
import {
  ExpenseAlreadyRevertedError,
  ExpenseCorrectionConflictError,
  ExpenseReferenceNotFoundError,
} from '#domain/errors/errors';
import {createExpenseReversal, validateExpenseCorrectionLinks} from '#domain/expense-correction/expense-correction';

describe('expense corrections', () => {
  const referencedExpense: IExpense = {
    id: 'expense-1',
    description: 'Dinner',
    userWhoPaidId: 'user-1',
    currencyId: 'currency-usd',
    eventId: 'event-1',
    expenseType: ExpenseType.Expense,
    splitInformation: [
      {userId: 'user-1', amount: 40, exchangedAmount: 50},
      {userId: 'user-2', amount: 60, exchangedAmount: 75},
    ],
    isCustomRate: false,
    revertsExpenseId: null,
    replacesExpenseId: null,
    createdAt: new Date('2026-01-01T00:00:00Z'),
    updatedAt: new Date('2026-01-01T00:00:00Z'),
  };

  it('returns the validated referenced expense for a reversal', () => {
    const result = validateExpenseCorrectionLinks(
      {
        eventId: 'event-1',
        revertsExpenseId: referencedExpense.id,
        replacesExpenseId: null,
      },
      referencedExpense,
      null,
    );

    expect(result).toEqual(success(referencedExpense));
  });

  it('returns reference not found when referenced expense is missing', () => {
    const result = validateExpenseCorrectionLinks(
      {
        eventId: 'event-1',
        revertsExpenseId: 'expense-1',
        replacesExpenseId: null,
      },
      null,
      null,
    );

    expect(result).toEqual(error(new ExpenseReferenceNotFoundError()));
  });

  it('returns reference not found when referenced expense belongs to another event', () => {
    const result = validateExpenseCorrectionLinks(
      {
        eventId: 'event-2',
        revertsExpenseId: referencedExpense.id,
        replacesExpenseId: null,
      },
      referencedExpense,
      null,
    );

    expect(result).toEqual(error(new ExpenseReferenceNotFoundError()));
  });

  it('returns reference not found when replaced expense belongs to another event', () => {
    const result = validateExpenseCorrectionLinks(
      {
        eventId: 'event-2',
        revertsExpenseId: null,
        replacesExpenseId: referencedExpense.id,
      },
      referencedExpense,
      null,
    );

    expect(result).toEqual(error(new ExpenseReferenceNotFoundError()));
  });

  it('returns conflict when both correction links are provided', () => {
    const result = validateExpenseCorrectionLinks(
      {
        eventId: 'event-1',
        revertsExpenseId: 'expense-1',
        replacesExpenseId: 'expense-1',
      },
      referencedExpense,
      null,
    );

    expect(result).toEqual(error(new ExpenseCorrectionConflictError()));
  });

  it('returns already reverted when duplicate revert is submitted', () => {
    const result = validateExpenseCorrectionLinks(
      {
        eventId: 'event-1',
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

  it.each([
    [ExpenseType.Expense, ExpenseType.Refund, false],
    [ExpenseType.Refund, ExpenseType.Expense, true],
  ])('creates an exact inverse of a %s with custom rate %s', (expenseType, reversedType, isCustomRate) => {
    const original: IExpense = {
      ...referencedExpense,
      expenseType,
      isCustomRate,
      splitInformation: [
        {userId: 'user-1', amount: 40, exchangedAmount: 50},
        {userId: 'user-1', amount: 40, exchangedAmount: 51},
        {userId: 'user-2', amount: 60, exchangedAmount: 75},
      ],
      revertsExpenseId: 'earlier-expense',
      replacesExpenseId: 'earlier-replacement',
    };
    const createdAt = new Date('2026-02-01T00:00:00Z');
    const reversal = createExpenseReversal(original, {description: 'Undo dinner', createdAt});

    expect(reversal).toEqual({
      id: expect.any(String),
      eventId: original.eventId,
      currencyId: original.currencyId,
      userWhoPaidId: original.userWhoPaidId,
      expenseType: reversedType,
      isCustomRate,
      splitInformation: [
        {userId: 'user-1', amount: -40, exchangedAmount: -50},
        {userId: 'user-1', amount: -40, exchangedAmount: -51},
        {userId: 'user-2', amount: -60, exchangedAmount: -75},
      ],
      revertsExpenseId: original.id,
      description: 'Undo dinner',
      createdAt,
      updatedAt: expect.any(Date),
    });
    expect(reversal.id).not.toBe(original.id);
    expect(reversal.splitInformation).not.toBe(original.splitInformation);
    expect(original.splitInformation[0]?.amount).toBe(40);
  });

  it('uses the current time when reversal creation time is omitted', () => {
    const before = Date.now();
    const reversal = createExpenseReversal(referencedExpense, {description: 'Undo dinner'});

    expect(reversal.createdAt.getTime()).toBeGreaterThanOrEqual(before);
    expect(reversal.createdAt.getTime()).toBeLessThanOrEqual(Date.now());
  });

  it('does not apply reversal constraints to a replacement', () => {
    const replacement: IExpense = {
      ...referencedExpense,
      id: 'replacement-1',
      expenseType: ExpenseType.Expense,
      splitInformation: [{userId: 'user-3', amount: 1, exchangedAmount: 2}],
      revertsExpenseId: null,
      replacesExpenseId: referencedExpense.id,
    };
    const result = validateExpenseCorrectionLinks(replacement, referencedExpense, null);

    expect(result).toEqual(success(referencedExpense));
  });
});
