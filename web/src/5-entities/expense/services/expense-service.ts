import {
  CreateExpenseForm,
  CreateExpenseRefundForm,
  Expense,
  ExpenseRefund,
  Tabs,
} from '@/5-entities/expense/types/types';
import {createExpense as createExpenseApi, getEventExpenses} from '@/5-entities/expense/services/api';
import {expenseStore} from '@/5-entities/expense/stores/expense-store';
import {ExpenseType} from '@/5-entities/expense/constants';
import {userStore} from '@/5-entities/user/stores/user-store';
import {currencyStore} from '@/5-entities/currency/stores/currency-store';
import {eventStore} from '@/5-entities/event/stores/event-store';
import retry from 'async-retry';
import {ulid} from 'ulid';
import {ApiError} from '@/6-shared/api/errors';

export class ExpenseService {
  private createExpenseKey: string | null = null;
  private createExpenseRefundKey: string | null = null;

  async createExpense(data: CreateExpenseForm, id: string, pinCode: string) {
    const {amount, splitOption, exchangeRate, ...rest} = data;

    let splitInformation;

    const isCurrenciesDifferent = rest.currencyId !== eventStore.currentEvent?.currencyId;
    const autoRate = isCurrenciesDifferent
      ? currencyStore.calculateExchangeRate(rest.currencyId, eventStore.currentEvent?.currencyId || '')
      : 1;

    const isCustomRate =
      isCurrenciesDifferent &&
      exchangeRate !== undefined &&
      Number(exchangeRate).toFixed(2) !== Number(autoRate).toFixed(2);

    if (isCustomRate && exchangeRate) {
      if (expenseStore.splitOption === '1') {
        const amountPerPerson = Number((Number(data.amount) / userStore.users.length).toFixed(2));
        splitInformation = userStore.users.map((u) => ({
          userId: u.id,
          amount: amountPerPerson,
          exchangedAmount: Number((amountPerPerson * exchangeRate).toFixed(2)),
        }));
      } else if (expenseStore.splitOption === '3') {
        splitInformation = this.splitByPercentages(Number(amount), data.splitInformation).map((i) => ({
          userId: i.userId,
          amount: i.amount,
          exchangedAmount: Number((i.amount * exchangeRate).toFixed(2)),
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
      } else if (expenseStore.splitOption === '3') {
        splitInformation = this.splitByPercentages(Number(amount), data.splitInformation);
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

    this.createExpenseKey = ulid();
    expenseStore.setIsCreatingExpense(true);
    try {
      const resp = await retry(
        async (bail) => {
          try {
            return await createExpenseApi({...body, expenseType: ExpenseType.Expense}, pinCode, this.createExpenseKey!);
          } catch (err) {
            const apiError = err as ApiError;
            if (apiError.statusCode && apiError.statusCode < 500) {
              bail(err as Error);
            }
            throw err;
          }
        },
        {retries: 2, factor: 2, minTimeout: 200},
      );
      expenseStore.setExpenses([...expenseStore.expenses, resp]);
    } finally {
      this.createExpenseKey = null;
      expenseStore.setIsCreatingExpense(false);
    }
  }

  async fetchExpenses(eventId: string, pinCode: string) {
    const expenses = await getEventExpenses(eventId, pinCode);

    expenseStore.setExpenses(expenses.filter((e: Expense | ExpenseRefund) => e.expenseType === ExpenseType.Expense));
    expenseStore.setExpenseRefunds(
      expenses.filter((e: Expense | ExpenseRefund) => e.expenseType === ExpenseType.Refund),
    );
  }

  async createExpenseRefund(expenseRefund: CreateExpenseRefundForm, pinCode: string) {
    const {userWhoReceiveId, amount, ...rest} = expenseRefund;

    const body = {
      ...rest,
      expenseType: ExpenseType.Refund,
      splitInformation: [{userId: userWhoReceiveId, amount: Number(Number(amount).toFixed(2))}],
    };

    this.createExpenseRefundKey = ulid();
    expenseStore.setIsCreatingExpenseRefund(true);
    try {
      const resp = await retry(
        async (bail) => {
          try {
            return await createExpenseApi(body, pinCode, this.createExpenseRefundKey!);
          } catch (err) {
            const apiError = err as ApiError;
            if (apiError.statusCode && apiError.statusCode < 500) {
              bail(err as Error);
            }
            throw err;
          }
        },
        {retries: 2, factor: 2, minTimeout: 200},
      );
      expenseStore.setExpenseRefunds([...expenseStore.expenseRefunds, resp]);
    } finally {
      this.createExpenseRefundKey = null;
      expenseStore.setIsCreatingExpenseRefund(false);
    }
  }

  setSplitOption(splitOption: '1' | '2' | '3') {
    expenseStore.setSplitOption(splitOption);
  }

  setCurrentTab(currentTab: Tabs) {
    expenseStore.setCurrentTab(currentTab);
  }

  private splitByPercentages(
    total: number,
    entries: Array<{userId: string; amount: number}>,
  ): Array<{userId: string; amount: number}> {
    const totalCents = Math.round(total * 100);
    const rawCents = entries.map((e) => (totalCents * e.amount) / 100);
    const flooredCents = rawCents.map(Math.floor);
    const remainders = rawCents.map((r, i) => r - flooredCents[i]);
    const shortfall = totalCents - flooredCents.reduce((s, c) => s + c, 0);
    const indices = entries.map((_, i) => i).sort((a, b) => remainders[b] - remainders[a]);
    const amounts = [...flooredCents];

    for (let i = 0; i < shortfall; i++) {
      amounts[indices[i]] += 1;
    }

    return entries.map((e, i) => ({userId: e.userId, amount: amounts[i] / 100}));
  }
}

export const expenseService = new ExpenseService();
