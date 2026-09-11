import {Result} from '#packages/result';

import {IEvent} from '#domain/entities/event.entity';
import {EventDeletedError, EventNotFoundError, InvalidPinCodeError} from '#domain/errors/errors';

export abstract class EventServiceAbstract {
  abstract isValidEvent(
    event: IEvent | null | undefined,
    pinCode: string,
  ): Result<boolean, EventNotFoundError | EventDeletedError | InvalidPinCodeError>;
  abstract isEventExists(event: IEvent | null | undefined): event is IEvent;
  abstract isEventNotDeleted(event: IEvent): Result<boolean, EventDeletedError>;
  abstract isValidPinCode(event: IEvent, pinCode: string): Result<boolean, InvalidPinCodeError>;
}
