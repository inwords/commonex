import {
  CreateExpenseForm,
  CreateExpenseRefundForm,
  Expense,
  ExpenseRefund,
  Tabs
} from '@/5-entities/expense/types/types';
import {createExpense as createExpenseApi, getEventExpenses} from '@/5-entities/expense/services/api';
import {expenseStore} from '@/5-entities/expense/stores/expense-store';
import {ExpenseType} from '@/5-entities/expense/constants';
import {userStore} from '@/5-entities/user/stores/user-store';
import {currencyStore} from '@/5-entities/currency/stores/currency-store';
import {eventStore} from '@/5-entities/event/stores/event-store';

export class ExpenseService {
  async createExpense(data: CreateExpenseForm, id: string, pinCode: string) {
    const {amount, splitOption, exchangeRate, ...rest} = data;

    let splitInformation;

    const isCurrenciesDifferent = rest.currencyId !== eventStore.currentEvent?.currencyId;
    const autoRate = isCurrenciesDifferent
      ? currencyStore.calculateExchangeRate(rest.currencyId, eventStore.currentEvent?.currencyId || '')
      : 1;

    const isCustomRate = isCurrenciesDifferent &&
                        exchangeRate !== undefined &&
                        exchangeRate !== autoRate;

    if (isCustomRate && exchangeRate) {
      if (expenseStore.splitOption === '1') {
        const amountPerPerson = Number((Number(data.amount) / userStore.users.length).toFixed(2));
        splitInformation = userStore.users.map((u) => ({
          userId: u.id,
          amount: amountPerPerson,
          exchangedAmount: Number((amountPerPerson * exchangeRate).toFixed(2)),
        }));
      } else {
        splitInformation = data.splitInformation.map((i) => ({
          userId: i.userId,
          amount: Number(Number(i.amount).toFixed(2)),
          exchangedAmount: Number((Number(i.amount) * exchangeRate).toFixed(2)),
        }));
      }
    } else {
      if (expenseStore.splitOption === '1') {
        splitInformation = userStore.users.map((u) => ({
          userId: u.id,
          amount: Number((Number(data.amount) / userStore.users.length).toFixed(2)),
        }));
      } else {
        splitInformation = data.splitInformation.map((i) => ({
          userId: i.userId,
          amount: Number(Number(i.amount).toFixed(2)),
        }));
      }
    }

    const body = {
      ...rest,
      eventId: id,
      splitInformation,
    };

    const resp = await createExpenseApi({...body, expenseType: ExpenseType.Expense}, pinCode);

    expenseStore.setExpenses([...expenseStore.expenses, resp]);
  }

  async fetchExpenses(eventId: string, pinCode: string) {
    const expenses = await getEventExpenses(eventId, pinCode);

    expenseStore.setExpenses(expenses.filter((e: Expense | ExpenseRefund) => e.expenseType === ExpenseType.Expense));
    expenseStore.setExpenseRefunds(expenses.filter((e: Expense | ExpenseRefund) => e.expenseType === ExpenseType.Refund));
  }

  async createExpenseRefund(expenseRefund: CreateExpenseRefundForm, pinCode: string) {
    const {userWhoReceiveId, amount, ...rest} = expenseRefund;

    const resp = await createExpenseApi({
      ...rest,
      expenseType: ExpenseType.Refund,
      splitInformation: [{userId: userWhoReceiveId, amount: Number(Number(amount).toFixed(2))}],
    }, pinCode);

    expenseStore.setExpenseRefunds([...expenseStore.expenseRefunds, resp]);
  }

  setSplitOption(splitOption: '1' | '2') {
    expenseStore.setSplitOption(splitOption);
  }

  setCurrentTab(currentTab: Tabs) {
    expenseStore.setCurrentTab(currentTab);
  }
}

export const expenseService = new ExpenseService();
