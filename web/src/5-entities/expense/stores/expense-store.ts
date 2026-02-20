import {CreateExpenseRefundForm, Expense, ExpenseRefund, Tabs} from '@/5-entities/expense/types/types';
import {makeAutoObservable} from 'mobx';
import {userStore} from '@/5-entities/user/stores/user-store';

export class ExpenseStore {
  expenses: Array<Expense> = [];
  expenseRefunds: Array<ExpenseRefund> = [];
  splitOption: '1' | '2' = '1';
  currentTab: Tabs = 0;
  isExpenseRefundModalOpen: boolean = false;
  currentExpenseRefund: Partial<CreateExpenseRefundForm> = {};
  isExpenseDetailsModalOpen: boolean = false;
  selectedExpenseForDetails: Expense | null = null;

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

  get currentUserDebts() {
    const debts = this.expenses.reduce<Record<string, number>>((prev, curr) => {
      if (curr.userWhoPaidId !== userStore.currentUser?.id) {
        prev[curr.userWhoPaidId] =
          (prev[curr.userWhoPaidId] || 0) +
          curr.splitInformation.reduce((pre, cur) => {
            if (cur.userId === userStore.currentUser?.id) {
              pre += cur.exchangedAmount;
            }

            return pre;
          }, 0);
      }

      return prev;
    }, {});

    this.expenseRefunds.forEach((r) => {
      if (userStore.currentUser?.id === r.userWhoPaidId) {
        r.splitInformation.forEach((i) => {
          if (debts[i.userId]) {
            debts[i.userId] -= i.exchangedAmount;
          }
        });
      }
    });

    return debts;
  }

  get totalExpensesAmount() {
    return this.expensesToView.reduce((sum, expense) => sum + expense.amount, 0);
  }

  get currentUserSpentAmount() {
    return this.expenses.reduce((sum, expense) => {
      const userSplit = expense.splitInformation.find((split) => split.userId === userStore.currentUser?.id);
      if (userSplit) {
        sum += userSplit.exchangedAmount;
      }
      return sum;
    }, 0);
  }

  setExpenses(expenses: Array<Expense>) {
    this.expenses = expenses;
  }

  setExpenseRefunds(expenseRefunds: Array<ExpenseRefund>) {
    this.expenseRefunds = expenseRefunds;
  }

  setSplitOption(splitOption: '1' | '2') {
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
}

export const expenseStore = new ExpenseStore();
