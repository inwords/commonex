import {Injectable} from '@nestjs/common';

import {Result, error, isError, success} from '#packages/result';
import {UseCase} from '#packages/use-case';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {IEvent} from '#domain/entities/event.entity';
import {IUserInfo} from '#domain/entities/user-info.entity';
import {EventDeletedError, EventNotFoundError, InvalidPinCodeError} from '#domain/errors/errors';

type Input = {eventId: string; pinCode: string};
type Output = Result<IEvent & {users: Array<IUserInfo>}, EventNotFoundError | EventDeletedError | InvalidPinCodeError>;

@Injectable()
export class GetEventInfoUseCase implements UseCase<Input, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly eventService: EventServiceAbstract,
  ) {}

  public async execute({eventId, pinCode}: Input): Promise<Output> {
    const [event] = await this.rDataService.event.findById(eventId);

    if (!this.eventService.isEventExists(event)) {
      return error(new EventNotFoundError());
    }

    const notDeletedResult = this.eventService.isEventNotDeleted(event);

    if (isError(notDeletedResult)) {
      return notDeletedResult;
    }

    const pinCodeResult = this.eventService.isValidPinCode(event, pinCode);

    if (isError(pinCodeResult)) {
      return pinCodeResult;
    }

    const [users] = await this.rDataService.userInfo.findByEventId(eventId);

    return success({...event, users});
  }
}
