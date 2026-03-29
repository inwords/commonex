import {User, UserDraft} from '@/5-entities/user/types/types';

export interface Event {
  id: string;
  name: string;
  currencyId: string;
  users: Array<User>;
  pinCode: string;
}

export type CreateEvent = Omit<Event, 'id' | 'users'> & {users: Array<UserDraft>};

export interface CreateEventShareTokenResponse {
  token: string;
  expiresAt: string;
}
