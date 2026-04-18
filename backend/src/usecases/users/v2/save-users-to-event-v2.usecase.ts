import {UseCase} from '#packages/use-case';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {IEvent} from '#domain/entities/event.entity';
import {IUserInfo} from '#domain/entities/user-info.entity';
import {UserInfoValueObject} from '#domain/value-objects/user-info.value-object';
import {Injectable} from '@nestjs/common';
import {Result, success, isError} from '#packages/result';
import {EventNotFoundError, EventDeletedError, InvalidPinCodeError} from '#domain/errors/errors';
import {IdempotencySharedUseCase} from '#usecases/shared/idempotency.usecase';

type InputCore = {users: Array<Omit<IUserInfo, 'id' | 'eventId'>>} & {pinCode: IEvent['pinCode']; eventId: IEvent['id']};
type Input = InputCore & {idempotencyKey?: string; url: string};
type Output = Result<Array<IUserInfo>, EventNotFoundError | EventDeletedError | InvalidPinCodeError>;

@Injectable()
export class SaveUsersToEventV2UseCase implements UseCase<Input, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly eventService: EventServiceAbstract,
    private readonly idempotencyUseCase: IdempotencySharedUseCase,
  ) {}

  public async execute(input: Input): Promise<Output> {
    const {idempotencyKey, url, ...core} = input;
    return this.idempotencyUseCase.execute(idempotencyKey, url, core, () => this.executeCore(core));
  }

  private async executeCore({eventId, users, pinCode}: InputCore): Promise<Output> {
    return this.rDataService.transaction(async (ctx) => {
      const [event] = await this.rDataService.event.findById(eventId, {
        ctx,
        lock: 'pessimistic_write',
        onLocked: 'nowait',
      });

      const validationResult = this.eventService.isValidEvent(event, pinCode);
      if (isError(validationResult)) {
        return validationResult;
      }

      const usersValue = users.map((u) => new UserInfoValueObject({...u, eventId}).value);

      await this.rDataService.userInfo.insert(usersValue, {ctx});

      return success(usersValue);
    });
  }
}
