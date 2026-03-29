import {httpClient} from '@/6-shared/api/http-client';
import {UserDraft} from '@/5-entities/user/types/types';

export const addUsersToEvent = async (eventId: string, users: Array<UserDraft>, pinCode: string) => {
  try {
    return await httpClient.request(`/v2/user/event/${eventId}/users`, {
      method: 'POST',
      body: JSON.stringify({
        users,
        pinCode,
      }),
    });
  } catch (error) {}
};
