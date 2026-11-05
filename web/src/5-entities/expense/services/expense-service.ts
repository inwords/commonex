import {analyticsService} from '@/6-shared/analytics/analytics-service';
import {
  CreateExpenseForm,
  CreateExpenseRefundForm,
  Expense,
  ExpenseRefund,
  Tabs,
} from '@/5-entities/expense/types/types';
import {createExpense as createExpenseApi, getEventExpenses, reviseExpense} from '@/5-entities/expense/services/api';
import {expenseStore} from '@/5-entities/expense/stores/expense-store';
import {ExpenseType} from '@/5-entities/expense/constants';
import {userStore} from '@/5-entities/user/stores/user-store';
import {currencyStore} from '@/5-entities/currency/stores/currency-store';
import {eventStore} from '@/5-entities/event/stores/event-store';
import retry from 'async-retry';
import {ulid} from 'ulid';
import {ApiError, ApiErrorCode} from '@/6-shared/api/errors';
import {buildExpenseRevision, canEditExpense} from '@/5-entities/expense/lib/expense-editing';

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
      expenseStore.addCreatedExpense(resp);
      analyticsService.track({name: 'expense_submitted'});
    } catch (error) {
      analyticsService.track({name: 'expense_failed', params: {reason: analyticsService.failureReason(error)}});
      throw error;
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
      analyticsService.track({name: 'settlement_recorded'});
    } catch (error) {
      analyticsService.track({name: 'settlement_failed', params: {reason: analyticsService.failureReason(error)}});
      throw error;
    } finally {
      this.createExpenseRefundKey = null;
      expenseStore.setIsCreatingExpenseRefund(false);
    }
  }

  async updateExpense(expense: Expense, data: CreateExpenseForm, pinCode: string) {
    if (!canEditExpense(eventStore.currentEvent, expense)) {
      throw {statusCode: 403, code: ApiErrorCode.EXPENSE_EDITING_NOT_SUPPORTED, message: 'Expense editing unavailable'};
    }
    if (expenseStore.isUpdatingExpense) {
      throw {statusCode: 409, code: ApiErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS, message: 'Save in progress'};
    }
    const revision = buildExpenseRevision(
      expense,
      data,
      eventStore.currentEvent!.currencyId,
      userStore.users.map(({id}) => id),
    );
    const key = ulid();
    expenseStore.setIsUpdatingExpense(true);
    try {
      const updated = await retry(
        async (bail) => {
          try {
            return await reviseExpense(expense.eventId, expense.id, revision, pinCode, key);
          } catch (error) {
            const apiError = error as ApiError;
            if (apiError.statusCode && apiError.statusCode < 500) {
              bail(error as Error);
              // bail rejects the outer promise; throwing here would still schedule a background retry.
              return;
            }
            throw error;
          }
        },
        {retries: 2, factor: 2, minTimeout: 200},
      );
      if (updated) {
        expenseStore.replaceExpense(updated);
      }
    } finally {
      expenseStore.setIsUpdatingExpense(false);
    }
  }

  async reloadExpense(expense: Expense, pinCode: string) {
    const expenses = await getEventExpenses(expense.eventId, pinCode);
    const current = expenses.find(
      (item): item is Expense => item.id === expense.id && item.expenseType === ExpenseType.Expense,
    );
    if (!current) {
      throw {statusCode: 404, code: ApiErrorCode.EXPENSE_NOT_FOUND, message: 'Expense not found'};
    }
    expenseStore.setExpenses(expenses.filter((item): item is Expense => item.expenseType === ExpenseType.Expense));
    expenseStore.setExpenseRefunds(
      expenses.filter((item): item is ExpenseRefund => item.expenseType === ExpenseType.Refund),
    );
    expenseStore.setSelectedExpenseForDetails(current);
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
