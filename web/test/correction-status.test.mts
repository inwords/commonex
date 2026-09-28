import assert from 'node:assert/strict';
import test from 'node:test';
import * as correctionStatus from '../src/5-entities/expense/lib/correction-status.ts';
import type {ExpenseBase} from '../src/5-entities/expense/types/types.ts';

type ExpenseTimelineOperation = Pick<
  ExpenseBase,
  'id' | 'revertsExpenseId' | 'replacesExpenseId' | 'userWhoPaidId' | 'splitInformation'
>;

test('builds statuses only for expenses targeted by a replacement or reversal', () => {
  const createdAt = '2026-06-01T12:00:00Z';
  const date = new Intl.DateTimeFormat(undefined, {dateStyle: 'long'}).format(new Date(createdAt));

  const statuses = correctionStatus.buildCorrectionStatusByExpenseId(
    [
      {createdAt},
      {createdAt, replacesExpenseId: 'replaced-expense'},
      {createdAt, revertsExpenseId: 'reverted-expense'},
    ],
    {editedOn: (value) => `Edited ${value}`, revertedOn: (value) => `Reverted ${value}`},
  );

  assert.deepStrictEqual(
    statuses,
    new Map([
      ['replaced-expense', `Edited ${date}`],
      ['reverted-expense', `Reverted ${date}`],
    ]),
  );
});

test('keeps corrected replacement values in the selected expense list', () => {
  const expensesToView = [
    {id: 'expense-1', description: 'Dinner', amount: 42.5, replacesExpenseId: null},
    {id: 'expense-2', description: 'Dinner corrected', amount: 48.75, replacesExpenseId: 'expense-1'},
  ];

  assert.deepStrictEqual(
    correctionStatus.selectVisibleOperations(1, {
      currentUserExpenses: [],
      expensesToView,
      currentUserExpenseRefunds: [],
    }),
    [
      {id: 'expense-1', description: 'Dinner', amount: 42.5, replacesExpenseId: null},
      {id: 'expense-2', description: 'Dinner corrected', amount: 48.75, replacesExpenseId: 'expense-1'},
    ],
  );
});

test('keeps correction refunds in the selected refund list', () => {
  const currentUserExpenseRefunds = [
    {id: 'refund-1', description: 'Dinner refund', amount: 42.5, replacesExpenseId: null},
    {id: 'refund-2', description: 'Dinner refund corrected', amount: 48.75, replacesExpenseId: 'refund-1'},
  ];

  assert.deepStrictEqual(
    correctionStatus.selectVisibleOperations(3, {
      currentUserExpenses: [],
      expensesToView: [],
      currentUserExpenseRefunds,
    }),
    [
      {id: 'refund-1', description: 'Dinner refund', amount: 42.5, replacesExpenseId: null},
      {id: 'refund-2', description: 'Dinner refund corrected', amount: 48.75, replacesExpenseId: 'refund-1'},
    ],
  );
});

test('projects a restored expense as visible and repayment-actionable after a reversal-of-reversal', () => {
  const expensesToView = [
    {
      id: 'expense-1',
      revertsExpenseId: null,
      replacesExpenseId: null,
      userWhoPaidId: 'creditor',
      splitInformation: [{userId: 'debtor', exchangedAmount: 12}],
    },
    {
      id: 'reversal-1',
      revertsExpenseId: 'expense-1',
      replacesExpenseId: null,
      userWhoPaidId: 'creditor',
      splitInformation: [{userId: 'debtor', exchangedAmount: -12}],
    },
    {
      id: 'expense-2',
      revertsExpenseId: 'reversal-1',
      replacesExpenseId: null,
      userWhoPaidId: 'creditor',
      splitInformation: [{userId: 'debtor', exchangedAmount: 12}],
    },
  ];

  const projectedExpenses = correctionStatus.projectVisibleExpenseTimeline(expensesToView, 'debtor', expensesToView);

  assert.deepStrictEqual(projectedExpenses, [
    {
      operation: {
        id: 'expense-1',
        revertsExpenseId: null,
        replacesExpenseId: null,
        userWhoPaidId: 'creditor',
        splitInformation: [{userId: 'debtor', exchangedAmount: 12}],
      },
      currentUserDebt: 12,
      isRepaymentEligible: false,
    },
    {
      operation: {
        id: 'reversal-1',
        revertsExpenseId: 'expense-1',
        replacesExpenseId: null,
        userWhoPaidId: 'creditor',
        splitInformation: [{userId: 'debtor', exchangedAmount: -12}],
      },
      currentUserDebt: -12,
      isRepaymentEligible: false,
    },
    {
      operation: {
        id: 'expense-2',
        revertsExpenseId: 'reversal-1',
        replacesExpenseId: null,
        userWhoPaidId: 'creditor',
        splitInformation: [{userId: 'debtor', exchangedAmount: 12}],
      },
      currentUserDebt: 12,
      isRepaymentEligible: true,
    },
  ]);
});

test('projects string exchange values from the runtime boundary as numeric restored-expense debt', () => {
  const expensesToView = [
    {
      id: 'expense-1',
      revertsExpenseId: null,
      replacesExpenseId: null,
      userWhoPaidId: 'creditor',
      splitInformation: [{userId: 'debtor', amount: 12, exchangedAmount: '12'}],
    },
    {
      id: 'reversal-1',
      revertsExpenseId: 'expense-1',
      replacesExpenseId: null,
      userWhoPaidId: 'creditor',
      splitInformation: [{userId: 'debtor', amount: -12, exchangedAmount: '-12'}],
    },
    {
      id: 'expense-2',
      revertsExpenseId: 'reversal-1',
      replacesExpenseId: null,
      userWhoPaidId: 'creditor',
      splitInformation: [{userId: 'debtor', amount: 12, exchangedAmount: '12'}],
    },
  ] as unknown as ExpenseTimelineOperation[];

  const projectedExpenses = correctionStatus.projectVisibleExpenseTimeline(expensesToView, 'debtor', expensesToView);

  assert.deepStrictEqual(
    projectedExpenses.map(({operation, currentUserDebt, isRepaymentEligible}) => ({
      id: operation.id,
      currentUserDebt,
      isRepaymentEligible,
    })),
    [
      {id: 'expense-1', currentUserDebt: 12, isRepaymentEligible: false},
      {id: 'reversal-1', currentUserDebt: -12, isRepaymentEligible: false},
      {id: 'expense-2', currentUserDebt: 12, isRepaymentEligible: true},
    ],
  );
});

test('projects a large timeline without rescanning correction links for each visible expense', () => {
  const operations = Array.from({length: 400}, (_, index) => ({
    id: `expense-${index}`,
    userWhoPaidId: 'creditor',
    splitInformation: [{userId: 'debtor', amount: 12, exchangedAmount: 12}],
  }));
  let correctionLinkReads = 0;
  const corrections = operations.map((operation, index) => ({
    get revertsExpenseId() {
      correctionLinkReads++;
      return index % 3 === 0 ? operation.id : null;
    },
    get replacesExpenseId() {
      correctionLinkReads++;
      return index % 3 === 1 ? operation.id : null;
    },
  }));

  const timeline = correctionStatus.projectVisibleExpenseTimeline(operations, 'debtor', corrections);

  assert.deepStrictEqual(
    timeline.map(({isRepaymentEligible}) => isRepaymentEligible),
    operations.map((_, index) => index % 3 === 2),
  );
  assert.ok(correctionLinkReads <= corrections.length * 4, `Read correction links ${correctionLinkReads} times`);
});

test('removes an original refund when a refund replaces it', () => {
  const refunds = [
    {id: 'refund-1', replacesExpenseId: null},
    {id: 'refund-2', replacesExpenseId: 'refund-1'},
  ];

  assert.deepStrictEqual(
    correctionStatus.deriveActiveLedger(refunds).map((operation) => operation.id),
    ['refund-2'],
  );
});

test('removes an expense replaced by a refund from the combined ledger', () => {
  const expenses = [
    {id: 'expense-1', replacesExpenseId: null},
    {id: 'expense-2', replacesExpenseId: null},
  ];
  const refunds = [{id: 'refund-1', replacesExpenseId: 'expense-1'}];

  const activeOperationIds = correctionStatus
    .deriveActiveLedger([...expenses, ...refunds])
    .map((operation) => operation.id);

  assert.strictEqual(activeOperationIds.includes('expense-1'), false);
  assert.strictEqual(activeOperationIds.includes('expense-2'), true);
  assert.strictEqual(activeOperationIds.includes('refund-1'), true);
});

test('rejects repayment for a corrected expense', () => {
  const originalExpense = {id: 'expense-1', userWhoPaidId: 'creditor'};

  assert.strictEqual(
    correctionStatus.isExpenseRepaymentEligible(originalExpense, 'debtor', 12, new Set(['expense-1'])),
    false,
  );
});

test('allows repayment for an uncorrected expense with a positive debt', () => {
  const expense = {id: 'expense-1', userWhoPaidId: 'creditor'};

  assert.strictEqual(correctionStatus.isExpenseRepaymentEligible(expense, 'debtor', 12, new Set()), true);
});

test('rejects repayment when the current user has no debt', () => {
  const expense = {id: 'expense-1', userWhoPaidId: 'creditor'};

  assert.strictEqual(correctionStatus.isExpenseRepaymentEligible(expense, 'debtor', 0, new Set()), false);
});

test('rejects repayment when the current user paid the expense', () => {
  const expense = {id: 'expense-1', userWhoPaidId: 'debtor'};

  assert.strictEqual(correctionStatus.isExpenseRepaymentEligible(expense, 'debtor', 12, new Set()), false);
});

test('cancels debts for both users when a reversal preserves the payer with negative splits', () => {
  const activeLedger = correctionStatus.deriveActiveLedger([
    {
      id: 'expense-1',
      replacesExpenseId: null,
      userWhoPaidId: 'A',
      splitInformation: [{userId: 'B', exchangedAmount: 100}],
    },
    {
      id: 'reversal-1',
      revertsExpenseId: 'expense-1',
      replacesExpenseId: null,
      userWhoPaidId: 'A',
      splitInformation: [{userId: 'B', exchangedAmount: -100}],
    },
  ]);

  assert.deepStrictEqual(correctionStatus.calculateCurrentUserDebts(activeLedger, 'B'), {});
  assert.deepStrictEqual(correctionStatus.calculateCurrentUserDebts(activeLedger, 'A'), {});
});

test('removes a reversed expense from spending totals and restores it after undo', () => {
  const expense = {
    id: 'expense-1',
    expenseType: 'expense' as const,
    userWhoPaidId: 'A',
    splitInformation: [{userId: 'B', amount: 100, exchangedAmount: 100}],
  };
  const reversal = {
    id: 'reversal-1',
    expenseType: 'refund' as const,
    revertsExpenseId: expense.id,
    userWhoPaidId: 'A',
    splitInformation: [{userId: 'B', amount: -100, exchangedAmount: -100}],
  };
  const undo = {
    id: 'undo-1',
    expenseType: 'expense' as const,
    revertsExpenseId: reversal.id,
    userWhoPaidId: 'A',
    splitInformation: [{userId: 'B', amount: 100, exchangedAmount: 100}],
  };

  assert.deepStrictEqual(correctionStatus.calculateSpendingTotals([expense, reversal], 'B'), {
    totalExpensesAmount: 0,
    currentUserSpentAmount: 0,
  });
  assert.deepStrictEqual(correctionStatus.calculateSpendingTotals([expense, reversal, undo], 'B'), {
    totalExpensesAmount: 100,
    currentUserSpentAmount: 100,
  });
});

test('keeps a repayment and its reversals out of spending totals', () => {
  const refund = {
    id: 'refund-1',
    expenseType: 'refund' as const,
    splitInformation: [{userId: 'B', amount: 40, exchangedAmount: 40}],
  };
  const reversal = {
    id: 'reversal-1',
    expenseType: 'expense' as const,
    revertsExpenseId: refund.id,
    splitInformation: [{userId: 'B', amount: -40, exchangedAmount: -40}],
  };
  const undo = {
    id: 'undo-1',
    expenseType: 'refund' as const,
    revertsExpenseId: reversal.id,
    splitInformation: [{userId: 'B', amount: 40, exchangedAmount: 40}],
  };

  assert.deepStrictEqual(correctionStatus.calculateSpendingTotals([refund, reversal, undo], 'B'), {
    totalExpensesAmount: 0,
    currentUserSpentAmount: 0,
  });
});

test('counts a replacement of an expense reversal as a spending correction', () => {
  const expense = {
    id: 'expense-1',
    expenseType: 'expense' as const,
    splitInformation: [{userId: 'B', amount: 100, exchangedAmount: 100}],
  };
  const reversal = {
    id: 'reversal-1',
    expenseType: 'refund' as const,
    revertsExpenseId: expense.id,
    splitInformation: [{userId: 'B', amount: -100, exchangedAmount: -100}],
  };
  const replacement = {
    id: 'replacement-1',
    expenseType: 'refund' as const,
    replacesExpenseId: reversal.id,
    splitInformation: [{userId: 'B', amount: -80, exchangedAmount: -80}],
  };

  assert.deepStrictEqual(correctionStatus.calculateSpendingTotals([expense, reversal, replacement], 'B'), {
    totalExpensesAmount: 20,
    currentUserSpentAmount: 20,
  });
});

test('uses the replacement type when an ordinary expense becomes a refund', () => {
  const original = {
    id: 'expense-1',
    expenseType: 'expense' as const,
    splitInformation: [{userId: 'B', amount: 100, exchangedAmount: 100}],
  };
  const replacement = {
    id: 'refund-1',
    expenseType: 'refund' as const,
    replacesExpenseId: original.id,
    splitInformation: [{userId: 'B', amount: 40, exchangedAmount: 40}],
  };

  assert.deepStrictEqual(correctionStatus.calculateSpendingTotals([original, replacement], 'B'), {
    totalExpensesAmount: 0,
    currentUserSpentAmount: 0,
  });
});

test('reduces a debt when the debtor records a normal refund', () => {
  const activeLedger = correctionStatus.deriveActiveLedger([
    {
      id: 'expense-1',
      replacesExpenseId: null,
      userWhoPaidId: 'A',
      splitInformation: [{userId: 'B', exchangedAmount: 100}],
    },
    {
      id: 'refund-1',
      replacesExpenseId: null,
      userWhoPaidId: 'B',
      splitInformation: [{userId: 'A', exchangedAmount: 40}],
    },
  ]);

  assert.deepStrictEqual(correctionStatus.calculateCurrentUserDebts(activeLedger, 'B'), {A: 60});
});

test('uses only the refund when it replaces an expense across buckets', () => {
  const activeLedger = correctionStatus.deriveActiveLedger([
    {
      id: 'expense-1',
      replacesExpenseId: null,
      userWhoPaidId: 'A',
      splitInformation: [{userId: 'B', exchangedAmount: 100}],
    },
    {
      id: 'refund-1',
      replacesExpenseId: 'expense-1',
      userWhoPaidId: 'B',
      splitInformation: [{userId: 'A', exchangedAmount: 40}],
    },
  ]);

  assert.deepStrictEqual(
    activeLedger.map((operation) => operation.id),
    ['refund-1'],
  );
  assert.deepStrictEqual(correctionStatus.calculateCurrentUserDebts(activeLedger, 'B'), {});
});

test('accounts for every duplicate split row from debtor and payer perspectives', () => {
  const activeLedger = correctionStatus.deriveActiveLedger([
    {
      id: 'expense-1',
      replacesExpenseId: null,
      userWhoPaidId: 'A',
      splitInformation: [
        {userId: 'B', exchangedAmount: 60},
        {userId: 'B', exchangedAmount: 40},
      ],
    },
  ]);

  assert.deepStrictEqual(correctionStatus.calculateCurrentUserDebts(activeLedger, 'B'), {A: 100});
  assert.deepStrictEqual(correctionStatus.calculateCurrentUserDebts(activeLedger, 'A'), {});
});
