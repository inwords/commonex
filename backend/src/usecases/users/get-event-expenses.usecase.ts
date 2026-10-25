import {Injectable} from '@nestjs/common';

import {Result, error, isError, success} from '#packages/result';
import {UseCase} from '#packages/use-case';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {IExpense} from '#domain/entities/expense.entity';
import {ClientUpdateRequiredError, EventDeletedError, EventNotFoundError} from '#domain/errors/errors';

import {CheckEventVersionSharedUseCase} from '#usecases/shared/check-event-version.usecase';

type Input = Pick<IExpense, 'eventId'> & {userAgent?: string | undefined};
type Output = Result<IExpense[], ClientUpdateRequiredError | EventNotFoundError | EventDeletedError>;

@Injectable()
export class GetEventExpensesUseCase implements UseCase<Input, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly eventService: EventServiceAbstract,
    private readonly checkEventVersionUseCase: CheckEventVersionSharedUseCase,
  ) {}

  public async execute({eventId, userAgent}: Input): Promise<Output> {
    const compatibility = await this.checkEventVersionUseCase.execute({eventId, userAgent});
    if (isError(compatibility)) {
      return compatibility;
    }

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
