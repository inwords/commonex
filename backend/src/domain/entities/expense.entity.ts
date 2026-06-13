export enum ExpenseType {
  Expense = 'expense',
  Refund = 'refund',
}

export interface IExpense {
  id: string;
  description: string;
  userWhoPaidId: string;
  currencyId: string;
  eventId: string;
  expenseType: ExpenseType;
  splitInformation: Array<ISplitInfo>;
  isCustomRate: boolean;
  revertsExpenseId?: string | null;
  replacesExpenseId?: string | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface ISplitInfo {
  userId: string;
  amount: number;
  exchangedAmount: number;
}
