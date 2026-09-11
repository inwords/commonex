import {Injectable} from '@nestjs/common';

import {Result, isError, success} from '#packages/result';
import {UseCase} from '#packages/use-case';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {EventDeletedError, EventNotFoundError, InvalidPinCodeError} from '#domain/errors/errors';
import {EventShareTokenValueObject} from '#domain/value-objects/event-share-token.value-object';

type Input = {eventId: string; pinCode: string};
type Output = Result<{token: string; expiresAt: string}, EventNotFoundError | EventDeletedError | InvalidPinCodeError>;

@Injectable()
export class CreateEventShareTokenV2UseCase implements UseCase<Input, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly eventService: EventServiceAbstract,
  ) {}

  public async execute({eventId, pinCode}: Input): Promise<Output> {
    const [event] = await this.rDataService.event.findById(eventId);

    const validationResult = this.eventService.isValidEvent(event, pinCode);

    if (isError(validationResult)) {
      return validationResult;
    }

    const [existingToken] = await this.rDataService.eventShareToken.findOneActiveByEventId(eventId);

    if (existingToken) {
      return success({token: existingToken.token, expiresAt: existingToken.expiresAt.toISOString()});
    }

    const shareToken = new EventShareTokenValueObject({eventId});

    await this.rDataService.eventShareToken.insert(shareToken.value);

    return success({token: shareToken.value.token, expiresAt: shareToken.value.expiresAt.toISOString()});
  }
}
