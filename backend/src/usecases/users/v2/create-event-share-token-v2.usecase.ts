import {Injectable} from '@nestjs/common';

import {Result, isError, success} from '#packages/result';
import {UseCase} from '#packages/use-case';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {
  ClientUpdateRequiredError,
  EventDeletedError,
  EventNotFoundError,
  InvalidPinCodeError,
} from '#domain/errors/errors';
import {EventShareTokenValueObject} from '#domain/value-objects/event-share-token.value-object';

import {CheckEventVersionSharedUseCase} from '#usecases/shared/check-event-version.usecase';

interface Input {
  userAgent?: string | undefined;
  eventId: string;
  pinCode: string;
}
type Output = Result<
  {token: string; expiresAt: string},
  ClientUpdateRequiredError | EventNotFoundError | EventDeletedError | InvalidPinCodeError
>;

@Injectable()
export class CreateEventShareTokenV2UseCase implements UseCase<Input, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly eventService: EventServiceAbstract,
    private readonly checkEventVersionUseCase: CheckEventVersionSharedUseCase,
  ) {}

  public async execute({eventId, pinCode, userAgent}: Input): Promise<Output> {
    const compatibility = await this.checkEventVersionUseCase.execute({eventId, userAgent});
    if (isError(compatibility)) {
      return compatibility;
    }

    return this.rDataService.transaction('READ COMMITTED', async (trx) => {
      const [event] = await this.rDataService.event.findById(eventId, {...trx, lock: 'pessimistic_write'});

      const validationResult = this.eventService.isValidEvent(event, pinCode);

      if (isError(validationResult)) {
        return validationResult;
      }

      const [existingToken] = await this.rDataService.eventShareToken.findByEventId(eventId, trx);

      if (existingToken && existingToken.expiresAt.getTime() >= Date.now()) {
        return success({token: existingToken.token, expiresAt: existingToken.expiresAt.toISOString()});
      }

      if (existingToken) {
        await this.rDataService.eventShareToken.deleteByToken(existingToken.token, trx);
      }

      const shareToken = new EventShareTokenValueObject({eventId});

      await this.rDataService.eventShareToken.insert(shareToken.value, trx);

      return success({token: shareToken.value.token, expiresAt: shareToken.value.expiresAt.toISOString()});
    });
  }
}
