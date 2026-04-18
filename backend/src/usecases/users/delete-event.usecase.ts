import {Injectable} from '@nestjs/common';
import {UseCase} from '#packages/use-case';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {IEvent} from '#domain/entities/event.entity';
import {isError, Result, success} from '#packages/result';
import {EventDeletedError, EventNotFoundError, InvalidPinCodeError} from '#domain/errors';
import {IdempotencySharedUseCase} from '#usecases/shared/idempotency.usecase';

type InputCore = {
  eventId: IEvent['id'];
  pinCode: string;
};
type Input = InputCore & {idempotencyKey?: string; url: string};

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
    private readonly idempotencyUseCase: IdempotencySharedUseCase,
  ) {}

  public async execute(input: Input): Promise<Output> {
    const {idempotencyKey, url, ...core} = input;
    return this.idempotencyUseCase.execute(idempotencyKey, url, core, () => this.executeCore(core));
  }

  private async executeCore({eventId, pinCode}: InputCore): Promise<Output> {
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
