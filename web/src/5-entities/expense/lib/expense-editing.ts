import type {Event} from '@/5-entities/event/types/types';
import type {CreateExpenseForm, Expense, ReviseExpense} from '@/5-entities/expense/types/types';
import {ExpenseType} from '@/5-entities/expense/constants';
import {getExpenseExchangeRate} from './exchange-rate';
import {isExpenseEditingEnabled} from '@/6-shared/config/feature-flags';
import {ApiErrorCode} from '@/6-shared/api/errors';

export const canEditExpense = (event: Pick<Event, 'id' | 'version'> | undefined, expense: Expense | null): boolean =>
  isExpenseEditingEnabled() &&
  event?.version === 2 &&
  expense?.eventId === event.id &&
  expense?.expenseType === ExpenseType.Expense &&
  Number.isSafeInteger(expense.version) &&
  (expense.version ?? 0) > 0;

const getSavedExchangeRate = (expense: Expense, eventCurrencyId: string | undefined): number | undefined => {
  if (expense.currencyId === eventCurrencyId) {
    return 1;
  }
  const split = expense.splitInformation.find(
    ({amount, exchangedAmount}) => amount !== 0 && exchangedAmount / amount > 0,
  );
  // Rounded zero conversions do not reveal the original custom rate.
  return split ? getExpenseExchangeRate({...expense, splitInformation: [split]}, eventCurrencyId) : undefined;
};

export const getExpenseEditForm = (expense: Expense, eventCurrencyId: string | undefined): CreateExpenseForm => ({
  description: expense.description,
  userWhoPaidId: expense.userWhoPaidId,
  currencyId: expense.currencyId,
  eventId: expense.eventId,
  splitOption: '2',
  splitInformation: expense.splitInformation.map(({userId, amount}) => ({userId, amount})),
  amount: expense.splitInformation.reduce((sum, split) => sum + split.amount, 0),
  exchangeRate: getSavedExchangeRate(expense, eventCurrencyId),
  isCustomRate: expense.isCustomRate,
});

const invalidDraft = (): never => {
  throw {statusCode: 400, code: ApiErrorCode.VALIDATION_ERROR, message: 'Invalid expense draft'};
};

export const buildExpenseRevision = (
  expense: Expense,
  data: CreateExpenseForm,
  eventCurrencyId: string,
  userIds: string[],
): ReviseExpense => {
  if (
    !Number.isSafeInteger(expense.version) ||
    (expense.version ?? 0) < 1 ||
    !userIds.includes(data.userWhoPaidId) ||
    !data.currencyId ||
    !data.description.trim()
  ) {
    invalidDraft();
  }

  let splits: Array<{userId: string; amount: number}>;
  if (data.splitOption === '1' || data.splitOption === '3') {
    const total = Number(data.amount);
    const entries =
      data.splitOption === '1'
        ? userIds.map((userId) => ({userId, amount: 100 / userIds.length}))
        : data.splitInformation.map(({userId, amount}) => ({userId, amount: Number(amount)}));
    if (
      !Number.isFinite(total) ||
      entries.length === 0 ||
      entries.some(({amount}) => !Number.isFinite(amount) || amount < 0) ||
      (data.splitOption === '3' && entries.reduce((sum, {amount}) => sum + amount, 0) !== 100)
    ) {
      invalidDraft();
    }
    const totalCents = Math.round(total * 100);
    const raw = entries.map(({amount}) => (totalCents * amount) / 100);
    const cents = raw.map(Math.floor);
    const remaining = totalCents - cents.reduce((sum, amount) => sum + amount, 0);
    const order = entries.map((_, index) => index).sort((a, b) => raw[b] - cents[b] - (raw[a] - cents[a]));
    for (let index = 0; index < remaining; index++) {
      cents[order[index]]++;
    }
    splits = entries.map(({userId}, index) => ({userId, amount: cents[index] / 100}));
  } else if (data.splitOption === '2') {
    splits = data.splitInformation.map(({userId, amount}) => ({userId, amount: Number(amount)}));
  } else {
    return invalidDraft();
  }

  if (
    splits.length === 0 ||
    new Set(splits.map(({userId}) => userId)).size !== splits.length ||
    splits.some(({userId, amount}) => !userIds.includes(userId) || !Number.isFinite(amount))
  ) {
    invalidDraft();
  }

  const isCustomRate = data.currencyId !== eventCurrencyId && (data.isCustomRate ?? expense.isCustomRate);
  const rate = Number(data.exchangeRate);

  const sameSplits =
    expense.splitInformation.length === splits.length &&
    splits.every(({userId, amount}) =>
      expense.splitInformation.some((saved) => saved.userId === userId && saved.amount === amount),
    );
  const preserveAmounts =
    data.currencyId === expense.currencyId &&
    sameSplits &&
    isCustomRate === expense.isCustomRate &&
    (!isCustomRate || data.exchangeRate === getSavedExchangeRate(expense, eventCurrencyId));
  if (isCustomRate && !preserveAmounts && (!Number.isFinite(rate) || rate <= 0)) {
    invalidDraft();
  }

  return {
    expectedVersion: expense.version!,
    description: data.description,
    userWhoPaidId: data.userWhoPaidId,
    currencyId: data.currencyId,
    createdAt: expense.createdAt,
    isCustomRate,
    splitInformation: preserveAmounts
      ? expense.splitInformation.map((split) => ({...split}))
      : splits.map(({userId, amount}) => ({
          userId,
          amount,
          ...(isCustomRate ? {exchangedAmount: Number((amount * rate).toFixed(2))} : {}),
        })),
  };
};
