import {Injectable} from '@nestjs/common';

import {Result, error, success} from '#packages/result';
import {UseCase} from '#packages/use-case';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {ITransaction} from '#domain/abstracts/relational-data-service/types';
import {SupportedCurrencyServiceAbstract} from '#domain/abstracts/supported-currency-service/supported-currency-service';
import {IEvent} from '#domain/entities/event.entity';
import {IUserInfo} from '#domain/entities/user-info.entity';
import {CurrencyNotFoundError} from '#domain/errors/errors';
import {EventValueObject} from '#domain/value-objects/event.value-object';
import {UserInfoValueObject} from '#domain/value-objects/user-info.value-object';

import {
  IdempotencyError,
  IdempotencyOperation,
  IdempotencySharedUseCase,
  IdempotentInput,
} from '#usecases/shared/idempotency.usecase';

interface InputCore {
  users: Omit<IUserInfo, 'id' | 'eventId'>[];
  event: Pick<IEvent, 'name' | 'currencyId' | 'pinCode'>;
}
type Input = InputCore & IdempotentInput;
type Output = Result<IEvent & {users: IUserInfo[]}, CurrencyNotFoundError | IdempotencyError>;

@Injectable()
export class SaveEventUseCase implements UseCase<Input, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly supportedCurrencyService: SupportedCurrencyServiceAbstract,
    private readonly idempotencyUseCase: IdempotencySharedUseCase,
  ) {}

  public async execute(input: Input): Promise<Output> {
    const {idempotencyKey, legacyOperationId, ...core} = input;
    return this.idempotencyUseCase.execute(
      idempotencyKey,
      IdempotencyOperation.CREATE_EVENT_V1,
      legacyOperationId,
      core,
      (trx) => this.executeCore(core, trx),
    );
  }

  private async executeCore({event, users}: InputCore, trx: ITransaction): Promise<Output> {
    const currency = await this.supportedCurrencyService.findById(event.currencyId, trx);

    if (!currency) {
      return error(new CurrencyNotFoundError());
    }

    const eventValueObject = new EventValueObject(event);

    await this.rDataService.event.insert(eventValueObject.value, trx);

    const usersValue = users.map((u) => new UserInfoValueObject({...u, eventId: eventValueObject.value.id}).value);

    await this.rDataService.userInfo.insert(usersValue, trx);

    return success({...eventValueObject.value, users: usersValue});
  }
}
