import {EventProtocolVersion} from './event-protocol-version.enum';

export interface IEvent {
  id: string;
  name: string;
  currencyId: string;
  pinCode: string;
  version: EventProtocolVersion;
  createdAt: Date;
  updatedAt: Date;
  deletedAt: Date | null;
}
