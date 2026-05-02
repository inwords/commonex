import {Injectable} from '@nestjs/common';
import {UseCase} from '#packages/use-case';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {IEvent} from '#domain/entities/event.entity';
import {isError, Result, success} from '#packages/result';
import {EventDeletedError, EventNotFoundError, InvalidPinCodeError} from '#domain/errors';

type Input = {
  eventId: IEvent['id'];
  pinCode: string;
};

type Output = Result<
  {
    id: IEvent['id'];
    deletedAt: Date;
  },
  EventNotFoundError | EventDeletedError | InvalidPinCodeError
>;

@Injectable()
export class DeleteEventUseCase implements UseCase<Input, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly eventService: EventServiceAbstract,
  ) {}

  public async execute({eventId, pinCode}: Input): Promise<Output> {
    return this.rDataService.transaction(async (ctx) => {
      const [event] = await this.rDataService.event.findById(eventId, {
        ctx,
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
        {ctx},
      );

      return success({
        id: eventId,
        deletedAt,
      });
    });
  }
}
