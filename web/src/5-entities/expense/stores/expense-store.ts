import {CreateExpenseRefundForm, Expense, ExpenseRefund, Tabs} from '@/5-entities/expense/types/types';
import {
  calculateCurrentUserDebts,
  calculateSpendingTotals,
  deriveActiveLedger,
} from '@/5-entities/expense/lib/correction-status';
import {makeAutoObservable} from 'mobx';
import {userStore} from '@/5-entities/user/stores/user-store';

export class ExpenseStore {
  expenses: Array<Expense> = [];
  expenseRefunds: Array<ExpenseRefund> = [];
  splitOption: '1' | '2' | '3' = '1';
  currentTab: Tabs = 0;
  isExpenseRefundModalOpen: boolean = false;
  currentExpenseRefund: Partial<CreateExpenseRefundForm> = {};
  isExpenseDetailsModalOpen: boolean = false;
  selectedExpenseForDetails: Expense | null = null;
  isCreatingExpense: boolean = false;
  isCreatingExpenseRefund: boolean = false;

  constructor() {
    makeAutoObservable(this);
  }

  get expensesToView() {
    return this.expenses.map((expense) => {
      return {...expense, amount: expense.splitInformation.reduce((prev, info) => prev + info.exchangedAmount, 0)};
    });
  }

  get currentUserExpenses() {
    return this.expensesToView.filter((e) => {
      return e.splitInformation.some((i) => i.userId === userStore.currentUser?.id);
    });
  }

  get expenseRefundsToView() {
    return this.expenseRefunds.map((expense) => {
      return {...expense, amount: expense.splitInformation.reduce((prev, info) => prev + info.exchangedAmount, 0)};
    });
  }

  get currentUserExpenseRefunds() {
    return this.expenseRefundsToView.filter((e) => {
      return e.splitInformation.some((i) => i.userId === userStore.currentUser?.id);
    });
  }

  get activeLedger() {
    return deriveActiveLedger([...this.expenses, ...this.expenseRefunds]);
  }

  get currentUserDebts() {
    return calculateCurrentUserDebts(this.activeLedger, userStore.currentUser?.id);
  }

  get spendingTotals() {
    return calculateSpendingTotals([...this.expenses, ...this.expenseRefunds], userStore.currentUser?.id);
  }

  get totalExpensesAmount() {
    return this.spendingTotals.totalExpensesAmount;
  }

  get currentUserSpentAmount() {
    return this.spendingTotals.currentUserSpentAmount;
  }

  setExpenses(expenses: Array<Expense>) {
    this.expenses = expenses;
  }

  setExpenseRefunds(expenseRefunds: Array<ExpenseRefund>) {
    this.expenseRefunds = expenseRefunds;
  }

  setSplitOption(splitOption: '1' | '2' | '3') {
    this.splitOption = splitOption;
  }

  setCurrentTab(currentTab: Tabs) {
    this.currentTab = currentTab;
  }

  setIsExpenseRefundModalOpen(isExpenseRefundModalOpen: boolean) {
    this.isExpenseRefundModalOpen = isExpenseRefundModalOpen;
  }

  setCurrentExpenseRefund(expenseRefund: Partial<CreateExpenseRefundForm>) {
    this.currentExpenseRefund = expenseRefund;
  }

  setIsExpenseDetailsModalOpen(isOpen: boolean) {
    this.isExpenseDetailsModalOpen = isOpen;
  }

  setSelectedExpenseForDetails(expense: Expense | null) {
    this.selectedExpenseForDetails = expense;
  }

  setIsCreatingExpense(value: boolean) {
    this.isCreatingExpense = value;
  }

  setIsCreatingExpenseRefund(value: boolean) {
    this.isCreatingExpenseRefund = value;
  }
}

export const expenseStore = new ExpenseStore();
