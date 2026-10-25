import {Injectable} from '@nestjs/common';

import {Result, isError, success} from '#packages/result';
import {UseCase} from '#packages/use-case';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {IEvent} from '#domain/entities/event.entity';
import {EventDeletedError, EventNotFoundError, InvalidPinCodeError} from '#domain/errors';
import {ClientUpdateRequiredError} from '#domain/errors/errors';

import {CheckEventVersionSharedUseCase} from '#usecases/shared/check-event-version.usecase';

interface Input {
  userAgent?: string | undefined;
  eventId: IEvent['id'];
  pinCode: string;
}

type Output = Result<
  {
    id: IEvent['id'];
    deletedAt: Date;
  },
  ClientUpdateRequiredError | EventNotFoundError | EventDeletedError | InvalidPinCodeError
>;

@Injectable()
export class DeleteEventUseCase implements UseCase<Input, Output> {
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

    return this.rDataService.transaction(async (trx) => {
      const [event] = await this.rDataService.event.findById(eventId, {
        ...trx,
        lock: 'pessimistic_write',
        onLocked: 'nowait',
      });

      const isValidResult = this.eventService.isValidEvent(event, pinCode);

      if (isError(isValidResult)) {
        return isValidResult;
      }

      const deletedAt = new Date();

      await this.rDataService.event.update(
        eventId,
        {
          deletedAt,
          updatedAt: new Date(),
        },
        trx,
      );

      return success({
        id: eventId,
        deletedAt,
      });
    });
  }
}
