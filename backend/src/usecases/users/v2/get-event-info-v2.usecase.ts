import {UseCase} from '#packages/use-case';

import {IEvent} from '#domain/entities/event.entity';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {IUserInfo} from '#domain/entities/user-info.entity';
import {Injectable} from '@nestjs/common';
import {Result, success, error, isError} from '#packages/result';
import {
  EventNotFoundError,
  EventDeletedError,
  InvalidPinCodeError,
  InvalidTokenError,
  TokenExpiredError,
} from '#domain/errors/errors';

type Input = {eventId: string; pinCode?: string | undefined; token?: string | undefined};
type Output = Result<
  IEvent & {users: Array<IUserInfo>},
  EventNotFoundError | EventDeletedError | InvalidPinCodeError | InvalidTokenError | TokenExpiredError
>;

@Injectable()
export class GetEventInfoV2UseCase implements UseCase<Input, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly eventService: EventServiceAbstract,
  ) {}

  public async execute({eventId, pinCode, token}: Input): Promise<Output> {
    const [event] = await this.rDataService.event.findById(eventId);

    if (!this.eventService.isEventExists(event)) {
      return error(new EventNotFoundError());
    }

    const notDeletedResult = this.eventService.isEventNotDeleted(event);

    if (isError(notDeletedResult)) {
      return notDeletedResult;
    }

    if (token) {
      const [shareToken] = await this.rDataService.eventShareToken.findByToken(token);

      if (!shareToken) {
        return error(new InvalidTokenError());
      }

      if (shareToken.eventId !== eventId) {
        return error(new InvalidTokenError());
      }

      if (shareToken.expiresAt < new Date()) {
        return error(new TokenExpiredError());
      }

      const [users] = await this.rDataService.userInfo.findByEventId(eventId);

      return success({...event, users});
    }

    if (!pinCode) {
      return error(new InvalidPinCodeError());
    }

    const pinCodeResult = this.eventService.isValidPinCode(event, pinCode);

    if (isError(pinCodeResult)) {
      return pinCodeResult;
    }

    const [users] = await this.rDataService.userInfo.findByEventId(eventId);

    return success({...event, users});
  }
}
