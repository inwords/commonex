import {Injectable} from '@nestjs/common';

import {Result, error, isError, success} from '#packages/result';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {IEvent} from '#domain/entities/event.entity';
import {EventDeletedError, EventNotFoundError, InvalidPinCodeError} from '#domain/errors/errors';

@Injectable()
export class EventService implements EventServiceAbstract {
  isValidEvent(
    event: IEvent | null | undefined,
    pinCode: string,
  ): Result<boolean, EventNotFoundError | EventDeletedError | InvalidPinCodeError> {
    const existsResult = this.isEventExists(event);

    if (!existsResult) {
      return error(new EventNotFoundError());
    }

    const notDeletedResult = this.isEventNotDeleted(event);

    if (isError(notDeletedResult)) {
      return notDeletedResult;
    }

    const pinCodeResult = this.isValidPinCode(event, pinCode);

    if (isError(pinCodeResult)) {
      return pinCodeResult;
    }

    return success(true);
  }

  isEventExists(event: IEvent | null | undefined): event is IEvent {
    return Boolean(event);
  }

  isEventNotDeleted(event: IEvent): Result<boolean, EventDeletedError> {
    if (event.deletedAt !== null) {
      return error(new EventDeletedError());
    }

    return success(true);
  }

  isValidPinCode(event: IEvent, pinCode: string): Result<boolean, InvalidPinCodeError> {
    if (event.pinCode !== pinCode) {
      return error(new InvalidPinCodeError());
    }

    return success(true);
  }
}
