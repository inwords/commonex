import {Injectable} from '@nestjs/common';

import {Result, error, isError, success} from '#packages/result';
import {UseCase} from '#packages/use-case';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {IExpense} from '#domain/entities/expense.entity';
import {
  ClientUpdateRequiredError,
  EventDeletedError,
  EventNotFoundError,
  ExpenseNotFoundError,
  InvalidPinCodeError,
} from '#domain/errors/errors';

import {CheckEventVersionSharedUseCase} from '#usecases/shared/check-event-version.usecase';

interface Input {
  userAgent?: string | undefined;
  eventId: string;
  id: string;
  pinCode: string;
}
type Output = Result<
  IExpense[],
  ClientUpdateRequiredError | EventNotFoundError | EventDeletedError | InvalidPinCodeError | ExpenseNotFoundError
>;

@Injectable()
export class GetExpenseHistoryV4UseCase implements UseCase<Input, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly eventService: EventServiceAbstract,
    private readonly checkEventVersionUseCase: CheckEventVersionSharedUseCase,
  ) {}

  public async execute({eventId, id, pinCode, userAgent}: Input): Promise<Output> {
    const compatibility = await this.checkEventVersionUseCase.execute({eventId, userAgent});
    if (isError(compatibility)) {
      return compatibility;
    }

    const [event] = await this.rDataService.event.findById(eventId);
    const validation = this.eventService.isValidEvent(event, pinCode);
    if (isError(validation)) {
      return validation;
    }
    const [history] = await this.rDataService.expense.findHistoryById(eventId, id);
    if (history.length === 0) {
      return error(new ExpenseNotFoundError());
    }
    return success(history);
  }
}
