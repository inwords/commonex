import {addUsersToEvent} from '@/5-entities/user/services/api';
import {UserDraft} from '@/5-entities/user/types/types';
import {eventStore} from '@/5-entities/event/stores/event-store';
import {userStore} from '@/5-entities/user/stores/user-store';

export class UserService {
  public async addUsersToEvent(users: Array<UserDraft>) {
    const currentEvent = eventStore.currentEvent;

    if (currentEvent) {
      const resp = await addUsersToEvent(currentEvent.id, users, currentEvent.pinCode);

      userStore.setUsers([...userStore.users, ...resp]);
    }
  }
}

export const userService = new UserService();
