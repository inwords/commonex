export interface User {
  id: string;
  name: string;
  eventId: string;
}

export type UserDraft = Pick<User, 'name'>;
