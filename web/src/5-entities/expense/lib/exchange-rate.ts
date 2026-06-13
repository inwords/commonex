type ExpenseSplitForRate = {
  amount: number;
  exchangedAmount: number;
};

type ExpenseForExchangeRate = {
  currencyId: string;
  splitInformation: ExpenseSplitForRate[];
};

export const getExpenseExchangeRate = (
  expense: ExpenseForExchangeRate,
  eventCurrencyId: string | undefined,
  options?: {decimals?: number},
): number => {
  if (eventCurrencyId == null || expense.currencyId === eventCurrencyId) {
    return 1;
  }

  const firstSplit = expense.splitInformation[0];
  if (firstSplit == null || firstSplit.amount <= 0) {
    return 1;
  }

  const rate = firstSplit.exchangedAmount / firstSplit.amount;
  if (options?.decimals != null) {
    return Number(rate.toFixed(options.decimals));
  }

  return rate;
};
