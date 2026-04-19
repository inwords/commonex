import {
  createEvent,
  getEventInfo as getEventInfoApi,
  createEventShareToken as createEventShareTokenApi,
} from '@/5-entities/event/services/api';
import {userStore} from '@/5-entities/user/stores/user-store';
import {CreateEvent} from '@/5-entities/event/types/types';
import {eventStore} from '@/5-entities/event/stores/event-store';
import retry from 'async-retry';
import {ulid} from 'ulid';
import {ApiError} from '@/6-shared/api/errors';

export class EventService {
  private createEventKey: string | null = null;
  async getEventInfo(eventId: string, params: {pinCode?: string; token?: string}) {
    const resp = await getEventInfoApi(eventId, params);

    userStore.setUsers(resp.users);
    eventStore.setCurrentEvent(resp);
  }

  async createEvent(data: CreateEvent) {
    this.createEventKey = ulid();
    eventStore.setIsCreatingEvent(true);
    try {
      const resp = await retry(
        async (bail) => {
          try {
            return await createEvent(data, this.createEventKey!);
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

      userStore.setUsers(resp.users);
      eventStore.setCurrentEvent(resp);

      return resp.id;
    } finally {
      this.createEventKey = null;
      eventStore.setIsCreatingEvent(false);
    }
  }

  async createEventShareToken(eventId: string, pinCode: string) {
    return await createEventShareTokenApi(eventId, pinCode);
  }
}

export const eventService = new EventService();
