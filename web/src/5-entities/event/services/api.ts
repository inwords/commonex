import {CreateEvent, CreateEventShareTokenResponse} from '@/5-entities/event/types/types';
import {httpClient} from '@/6-shared/api/http-client';

export const createEvent = async (event: CreateEvent, idempotencyKey: string) => {
  return await httpClient.request('/user/event', {
    method: 'POST',
    body: JSON.stringify(event),
    idempotencyKey,
  });
};

export const getEventInfo = async (eventId: string, params: {pinCode?: string; token?: string}) => {
  try {
    const fetchUrl = `/v2/user/event/${eventId}`;

    return await httpClient.request(fetchUrl, {
      method: 'POST',
      body: JSON.stringify(params),
    });
  } catch (error) {
    console.error('An error occurred:', error);
  }
};

export const createEventShareToken = async (
  eventId: string,
  pinCode: string,
): Promise<CreateEventShareTokenResponse> => {
  const fetchUrl = `/v2/user/event/${eventId}/share-token`;

  return await httpClient.request(fetchUrl, {
    method: 'POST',
    body: JSON.stringify({pinCode}),
  });
};
