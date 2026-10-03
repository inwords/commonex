import {Injectable} from '@nestjs/common';

import {Result, isError, success} from '#packages/result';
import {UseCase} from '#packages/use-case';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {ITransaction} from '#domain/abstracts/relational-data-service/types';
import {IEvent} from '#domain/entities/event.entity';
import {IUserInfo} from '#domain/entities/user-info.entity';
import {EventDeletedError, EventNotFoundError, InvalidPinCodeError} from '#domain/errors/errors';
import {UserInfoValueObject} from '#domain/value-objects/user-info.value-object';

import {
  IdempotencyError,
  IdempotencyOperation,
  IdempotencySharedUseCase,
  IdempotentInput,
} from '#usecases/shared/idempotency.usecase';

type InputCore = {users: Omit<IUserInfo, 'id' | 'eventId'>[]} & {
  pinCode: IEvent['pinCode'];
  eventId: IEvent['id'];
};
type Input = InputCore & IdempotentInput;
type Output = Result<IUserInfo[], EventNotFoundError | EventDeletedError | InvalidPinCodeError | IdempotencyError>;

@Injectable()
export class SaveUsersToEventUseCase implements UseCase<Input, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly eventService: EventServiceAbstract,
    private readonly idempotencyUseCase: IdempotencySharedUseCase,
  ) {}

  public async execute(input: Input): Promise<Output> {
    const {idempotencyKey, legacyOperationId, ...core} = input;
    return this.idempotencyUseCase.execute(
      idempotencyKey,
      IdempotencyOperation.ADD_USERS_TO_EVENT_V1,
      legacyOperationId,
      core,
      (trx) => this.executeCore(core, trx),
    );
  }

  private async executeCore({eventId, users, pinCode}: InputCore, trx: ITransaction): Promise<Output> {
    const [event] = await this.rDataService.event.findById(eventId, trx);

    const validationResult = this.eventService.isValidEvent(event, pinCode);
    if (isError(validationResult)) {
      return validationResult;
    }

    const usersValue = users.map((u) => new UserInfoValueObject({...u, eventId}).value);

    await this.rDataService.userInfo.insert(usersValue, trx);

    return success(usersValue);
  }
}
