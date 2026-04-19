import {addUsersToEvent} from '@/5-entities/user/services/api';
import {UserDraft} from '@/5-entities/user/types/types';
import {eventStore} from '@/5-entities/event/stores/event-store';
import {userStore} from '@/5-entities/user/stores/user-store';
import retry from 'async-retry';
import {ulid} from 'ulid';
import {ApiError} from '@/6-shared/api/errors';

export class UserService {
  private addUsersKey: string | null = null;

  public async addUsersToEvent(users: Array<UserDraft>) {
    const currentEvent = eventStore.currentEvent;

    if (!currentEvent) {
      return;
    }

    this.addUsersKey = ulid();
    userStore.setIsAddingUsers(true);
    try {
      const resp = await retry(
        async (bail) => {
          try {
            return await addUsersToEvent(currentEvent.id, users, currentEvent.pinCode, this.addUsersKey!);
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

      userStore.setUsers([...userStore.users, ...resp]);
    } finally {
      this.addUsersKey = null;
      userStore.setIsAddingUsers(false);
    }
  }
}

export const userService = new UserService();
