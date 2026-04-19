import {CreateExpense} from '@/5-entities/expense/types/types';
import {httpClient} from '@/6-shared/api/http-client';

export const getEventExpenses = async (eventId: string, pinCode: string) => {
  try {
    const fetchUrl = `/v2/user/event/${eventId}/expenses`;

    return await httpClient.request(fetchUrl, {
      method: 'POST',
      body: JSON.stringify({pinCode}),
    });
  } catch (error) {
    console.error('An error occurred:', error);
  }
};

export const createExpense = async (expense: CreateExpense, pinCode: string, idempotencyKey: string) => {
  const {eventId, ...rest} = expense;

  return await httpClient.request(`/v2/user/event/${eventId}/expense`, {
    method: 'POST',
    body: JSON.stringify({...rest, pinCode}),
    idempotencyKey,
  });
};
