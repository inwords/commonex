import {EventVersion} from './event-version.enum';

export interface IEvent {
  id: string;
  name: string;
  currencyId: string;
  pinCode: string;
  version: EventVersion;
  createdAt: Date;
  updatedAt: Date;
  deletedAt: Date | null;
}
