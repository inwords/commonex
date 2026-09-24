import type {ExpenseBase, Tabs} from '@/5-entities/expense/types/types';

type CorrectionStatusContent = {
  editedOn: (date: string) => string;
  revertedOn: (date: string) => string;
};

type CorrectionOperation = Pick<ExpenseBase, 'createdAt' | 'revertsExpenseId' | 'replacesExpenseId'>;

type VisibleOperationLists<T> = {
  currentUserExpenses: T[];
  expensesToView: T[];
  currentUserExpenseRefunds: T[];
};

type ExpenseTimelineOperation = Pick<ExpenseBase, 'id' | 'userWhoPaidId' | 'splitInformation'>;

type SpendingOperation = Pick<ExpenseBase, 'id' | 'revertsExpenseId' | 'replacesExpenseId' | 'splitInformation'> & {
  expenseType: 'expense' | 'refund';
};

type SpendingCategory = {countsTowardSpending: boolean; isReversalLineage: boolean};

type VisibleExpenseTimelineItem<T extends ExpenseTimelineOperation> = {
  operation: T;
  currentUserDebt: number;
  isRepaymentEligible: boolean;
};

const formatCorrectionDate = (createdAt: string): string => {
  return new Intl.DateTimeFormat(undefined, {dateStyle: 'long'}).format(new Date(createdAt));
};

export const buildCorrectionStatusByExpenseId = (
  operations: CorrectionOperation[],
  content: CorrectionStatusContent,
): Map<string, string> => {
  const statuses = new Map<string, string>();

  for (const operation of operations) {
    if (operation.revertsExpenseId == null && operation.replacesExpenseId == null) {
      continue;
    }
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

export const selectVisibleOperations = <T>(currentTab: Tabs, operationLists: VisibleOperationLists<T>): T[] => {
  switch (currentTab) {
    case 0:
      return operationLists.currentUserExpenses;
    case 1:
      return operationLists.expensesToView;
    case 3:
      return operationLists.currentUserExpenseRefunds;
    default:
      return [];
  }
};

export const projectVisibleExpenseTimeline = <T extends ExpenseTimelineOperation>(
  visibleOperations: T[],
  currentUserId: string | undefined,
  allOperations: Array<Pick<ExpenseBase, 'revertsExpenseId' | 'replacesExpenseId'>>,
): Array<VisibleExpenseTimelineItem<T>> => {
  const correctedExpenseIds = new Set<string>();
  for (const {revertsExpenseId, replacesExpenseId} of allOperations) {
    if (revertsExpenseId != null) {
      correctedExpenseIds.add(revertsExpenseId);
    }
    if (replacesExpenseId != null) {
      correctedExpenseIds.add(replacesExpenseId);
    }
  }

  return visibleOperations.map((operation) => {
    const currentUserDebt = operation.splitInformation.reduce((amount, split) => {
      return split.userId === currentUserId ? amount + Number(split.exchangedAmount) : amount;
    }, 0);

    return {
      operation,
      currentUserDebt,
      isRepaymentEligible: isExpenseRepaymentEligible(operation, currentUserId, currentUserDebt, correctedExpenseIds),
    };
  });
};

export const deriveActiveLedger = <T extends Pick<ExpenseBase, 'id' | 'replacesExpenseId'>>(operations: T[]): T[] => {
  const replacedExpenseIds = new Set(
    operations.map((operation) => operation.replacesExpenseId).filter((id): id is string => id != null),
  );

  return operations.filter((operation) => !replacedExpenseIds.has(operation.id));
};

export const calculateSpendingTotals = (
  operations: SpendingOperation[],
  currentUserId: string | undefined,
): {totalExpensesAmount: number; currentUserSpentAmount: number} => {
  const operationsById = new Map(operations.map((operation) => [operation.id, operation]));
  const spendingCategoryById = new Map<string, SpendingCategory>();

  const getSpendingCategory = (operation: SpendingOperation): SpendingCategory => {
    const cached = spendingCategoryById.get(operation.id);
    if (cached !== undefined) {
      return cached;
    }

    let category: SpendingCategory;
    if (operation.revertsExpenseId != null) {
      const referenced = operationsById.get(operation.revertsExpenseId);
      category = {
        countsTowardSpending:
          referenced == null
            ? operation.expenseType === 'expense'
            : getSpendingCategory(referenced).countsTowardSpending,
        isReversalLineage: true,
      };
    } else if (operation.replacesExpenseId != null) {
      const replaced = operationsById.get(operation.replacesExpenseId);
      const replacedCategory = replaced == null ? undefined : getSpendingCategory(replaced);
      category = replacedCategory?.isReversalLineage
        ? replacedCategory
        : {countsTowardSpending: operation.expenseType === 'expense', isReversalLineage: false};
    } else {
      category = {countsTowardSpending: operation.expenseType === 'expense', isReversalLineage: false};
    }

    spendingCategoryById.set(operation.id, category);
    return category;
  };

  return deriveActiveLedger(operations).reduce(
    (totals, operation) => {
      if (!getSpendingCategory(operation).countsTowardSpending) {
        return totals;
      }

      for (const split of operation.splitInformation) {
        totals.totalExpensesAmount += split.exchangedAmount;
        if (split.userId === currentUserId) {
          totals.currentUserSpentAmount += split.exchangedAmount;
        }
      }
      return totals;
    },
    {totalExpensesAmount: 0, currentUserSpentAmount: 0},
  );
};

export const isExpenseRepaymentEligible = (
  expense: Pick<ExpenseBase, 'id' | 'userWhoPaidId'>,
  currentUserId: string | undefined,
  currentUserDebt: number,
  correctedExpenseIds: ReadonlySet<string>,
): boolean => {
  return (
    currentUserId != null &&
    expense.userWhoPaidId !== currentUserId &&
    currentUserDebt > 0 &&
    !correctedExpenseIds.has(expense.id)
  );
};

export const calculateCurrentUserDebts = (
  activeLedger: Array<Pick<ExpenseBase, 'userWhoPaidId' | 'splitInformation'>>,
  currentUserId: string | undefined,
): Record<string, number> => {
  if (currentUserId == null) {
    return {};
  }

  const balances = activeLedger.reduce<Record<string, number>>((currentBalances, operation) => {
    if (operation.userWhoPaidId !== currentUserId) {
      const currentUserSplitAmount = operation.splitInformation.reduce((amount, split) => {
        return split.userId === currentUserId ? amount + split.exchangedAmount : amount;
      }, 0);

      if (currentUserSplitAmount !== 0) {
        currentBalances[operation.userWhoPaidId] =
          (currentBalances[operation.userWhoPaidId] || 0) + currentUserSplitAmount;
      }

      return currentBalances;
    }

    operation.splitInformation.forEach((split) => {
      if (split.userId !== currentUserId) {
        currentBalances[split.userId] = (currentBalances[split.userId] || 0) - split.exchangedAmount;
      }
    });

    return currentBalances;
  }, {});

  return Object.fromEntries(Object.entries(balances).filter(([, amount]) => amount > 0));
};
