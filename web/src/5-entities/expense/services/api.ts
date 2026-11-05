import {CreateExpense, Expense, ExpenseRefund, ReviseExpense} from '@/5-entities/expense/types/types';
import {httpClient} from '@/6-shared/api/http-client';

export const getEventExpenses = async (eventId: string, pinCode: string): Promise<Array<Expense | ExpenseRefund>> =>
  httpClient.request(`/v2/user/event/${eventId}/expenses`, {
    method: 'POST',
    body: JSON.stringify({pinCode}),
  });

export const createExpense = async (expense: CreateExpense, pinCode: string, idempotencyKey: string) => {
  const {eventId, ...rest} = expense;

  return await httpClient.request(`/v2/user/event/${eventId}/expense`, {
    method: 'POST',
    body: JSON.stringify({...rest, pinCode}),
    idempotencyKey,
  });
};

export const reviseExpense = async (
  eventId: string,
  id: string,
  revision: ReviseExpense,
  pinCode: string,
  idempotencyKey: string,
): Promise<Expense> =>
  httpClient.request(`/v4/user/event/${eventId}/expense/${id}/revisions`, {
    method: 'POST',
    body: JSON.stringify({pinCode, ...revision}),
    idempotencyKey,
  });
