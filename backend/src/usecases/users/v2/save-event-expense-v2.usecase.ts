import {Injectable} from '@nestjs/common';

import {Result, error, isError} from '#packages/result';
import {UseCase} from '#packages/use-case';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {ITransaction} from '#domain/abstracts/relational-data-service/types';
import {IExpense} from '#domain/entities/expense.entity';
import {
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
  EventDeletedError,
  EventNotFoundError,
  InconsistentExchangedAmountError,
  InvalidPinCodeError,
} from '#domain/errors/errors';

import {
  IdempotencyError,
  IdempotencyOperation,
  IdempotencySharedUseCase,
  IdempotentInput,
} from '#usecases/shared/idempotency.usecase';
import {SaveEventExpenseInput, SaveEventExpenseSharedUseCase} from '#usecases/shared/save-event-expense.usecase';

type InputCore = SaveEventExpenseInput & {pinCode: string};
type Input = InputCore & IdempotentInput;
type Output = Result<
  IExpense,
  | EventNotFoundError
  | EventDeletedError
  | InvalidPinCodeError
  | CurrencyNotFoundError
  | CurrencyRateNotFoundError
  | InconsistentExchangedAmountError
  | IdempotencyError
>;

@Injectable()
export class SaveEventExpenseV2UseCase implements UseCase<Input, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly eventService: EventServiceAbstract,
    private readonly saveEventExpenseSharedUseCase: SaveEventExpenseSharedUseCase,
    private readonly idempotencyUseCase: IdempotencySharedUseCase,
  ) {}

  public async execute(input: Input): Promise<Output> {
    const {idempotencyKey, ...core} = input;
    return this.idempotencyUseCase.execute(idempotencyKey, IdempotencyOperation.CREATE_EVENT_EXPENSE_V2, core, (trx) =>
      this.executeCore(core, trx),
    );
  }

  private async executeCore(input: InputCore, trx: ITransaction): Promise<Output> {
    const {pinCode, ...expense} = input;
    const [event] = await this.rDataService.event.findById(input.eventId, {
      ...trx,
      lock: 'pessimistic_write',
      onLocked: 'nowait',
    });
    if (!this.eventService.isEventExists(event)) {
      return error(new EventNotFoundError());
    }
    const validation = this.eventService.isValidEvent(event, pinCode);
    if (isError(validation)) {
      return validation;
    }
    return this.saveEventExpenseSharedUseCase.execute(expense, event, trx);
  }
}
