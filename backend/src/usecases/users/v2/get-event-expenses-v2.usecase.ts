import {Injectable} from '@nestjs/common';

import {Result, isError, success} from '#packages/result';
import {UseCase} from '#packages/use-case';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {IExpense} from '#domain/entities/expense.entity';
import {
  ClientUpdateRequiredError,
  EventDeletedError,
  EventNotFoundError,
  InvalidPinCodeError,
} from '#domain/errors/errors';

import {CheckEventVersionSharedUseCase} from '#usecases/shared/check-event-version.usecase';

interface Input {
  userAgent?: string | undefined;
  eventId: string;
  pinCode: string;
}
type Output = Result<
  IExpense[],
  ClientUpdateRequiredError | EventNotFoundError | EventDeletedError | InvalidPinCodeError
>;

@Injectable()
export class GetEventExpensesV2UseCase implements UseCase<Input, Output> {
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

    const [event] = await this.rDataService.event.findById(eventId);

    const validationResult = this.eventService.isValidEvent(event, pinCode);

    if (isError(validationResult)) {
      return validationResult;
    }

    const [expenses] = await this.rDataService.expense.findByEventId(eventId);

    return success(expenses);
  }
}
