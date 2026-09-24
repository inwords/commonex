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

  const split = expense.splitInformation.find((split) => split.amount !== 0);
  if (split == null) {
    return 1;
  }

  const rate = split.exchangedAmount / split.amount;
  if (options?.decimals != null) {
    return Number(rate.toFixed(options.decimals));
  }

  return rate;
};
