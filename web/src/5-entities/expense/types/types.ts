import {ExpenseType} from '@/5-entities/expense/constants';

export interface CreateExpenseForm {
  description: string;
  userWhoPaidId: string;
  currencyId: string;
  eventId: string;
  splitInformation: Array<Omit<SplitInfo, 'exchangedAmount'>>;
  amount: number;
  splitOption: string;
  exchangeRate?: number;
  isCustomRate?: boolean;
}

export interface CreateExpenseRefundForm {
  description: string;
  userWhoPaidId: string;
  currencyId: string;
  eventId: string;
  amount: number;
  userWhoReceiveId: string;
}

export interface CreateExpense extends Omit<CreateExpenseForm, 'amount' | 'splitOption'> {
  expenseType: ExpenseType;
}

export interface SplitInfo {
  userId: string;
  amount: number;
  exchangedAmount: number;
}

export interface ExpenseBase {
  id: string;
  version?: number;
  updatedAt?: string;
  description: string;
  userWhoPaidId: string;
  currencyId: string;
  eventId: string;
  splitInformation: Array<SplitInfo>;
  isCustomRate: boolean;
  createdAt: string;
}

export interface Expense extends ExpenseBase {
  expenseType: ExpenseType.Expense;
}

export interface ExpenseRefund extends ExpenseBase {
  expenseType: ExpenseType.Refund;
}

export type Tabs = 0 | 1 | 2 | 3 | 4;

export interface ReviseExpense {
  expectedVersion: number;
  description: string;
  userWhoPaidId: string;
  currencyId: string;
  splitInformation: Array<Omit<SplitInfo, 'exchangedAmount'> & {exchangedAmount?: number}>;
  createdAt: string;
  isCustomRate: boolean;
}
