import {Injectable} from '@nestjs/common';

import {Result, error, isError, success} from '#packages/result';
import {UseCase} from '#packages/use-case';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {IExpense} from '#domain/entities/expense.entity';
import {EventDeletedError, EventNotFoundError} from '#domain/errors/errors';

type Input = Pick<IExpense, 'eventId'>;
type Output = Result<IExpense[], EventNotFoundError | EventDeletedError>;

@Injectable()
export class GetEventExpensesUseCase implements UseCase<Input, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly eventService: EventServiceAbstract,
  ) {}

  public async execute({eventId}: Input): Promise<Output> {
    const [event] = await this.rDataService.event.findById(eventId);

    if (!this.eventService.isEventExists(event)) {
      return error(new EventNotFoundError());
    }

    const notDeletedResult = this.eventService.isEventNotDeleted(event);

    if (isError(notDeletedResult)) {
      return notDeletedResult;
    }

    const [expenses] = await this.rDataService.expense.findByEventId(eventId);

    return success(expenses);
  }
}
