import {Injectable} from '@nestjs/common';

import {Result, error, isError, success} from '#packages/result';
import {UseCase} from '#packages/use-case';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {ITransaction} from '#domain/abstracts/relational-data-service/types';
import {SupportedCurrencyServiceAbstract} from '#domain/abstracts/supported-currency-service/supported-currency-service';
import {EventVersion} from '#domain/entities/event-version.enum';
import {IEvent} from '#domain/entities/event.entity';
import {IUserInfo} from '#domain/entities/user-info.entity';
import {ClientUpdateRequiredError, CurrencyNotFoundError} from '#domain/errors/errors';
import {EventValueObject} from '#domain/value-objects/event.value-object';
import {UserInfoValueObject} from '#domain/value-objects/user-info.value-object';

import {CheckEventVersionSharedUseCase} from '#usecases/shared/check-event-version.usecase';
import {getMobileVersion} from '#usecases/shared/get-mobile-version';
import {
  IdempotencyError,
  IdempotencyOperation,
  IdempotencySharedUseCase,
  IdempotentInput,
} from '#usecases/shared/idempotency.usecase';

interface InputCore {
  users: Omit<IUserInfo, 'id' | 'eventId'>[];
  event: Pick<IEvent, 'name' | 'currencyId' | 'pinCode'> & Partial<Pick<IEvent, 'version'>>;
}
type Input = InputCore & IdempotentInput & {userAgent?: string | undefined};
type Output = Result<
  IEvent & {users: IUserInfo[]},
  ClientUpdateRequiredError | CurrencyNotFoundError | IdempotencyError
>;

@Injectable()
export class SaveEventUseCase implements UseCase<Input, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly supportedCurrencyService: SupportedCurrencyServiceAbstract,
    private readonly idempotencyUseCase: IdempotencySharedUseCase,
    private readonly checkEventVersionUseCase: CheckEventVersionSharedUseCase,
  ) {}

  public async execute(input: Input): Promise<Output> {
    const compatibility = await this.checkEventVersionUseCase.execute({
      userAgent: input.userAgent,
      requestedVersion: input.event.version,
    });
    if (isError(compatibility)) {
      return compatibility;
    }

    const {idempotencyKey, userAgent, ...core} = input;
    const mobileVersion = getMobileVersion(userAgent);
    if (mobileVersion !== undefined) {
      core.event = {
        ...core.event,
        version:
          Number.isSafeInteger(mobileVersion) && mobileVersion >= 11
            ? EventVersion.ExpenseEditing
            : EventVersion.Legacy,
      };
    }
    return this.idempotencyUseCase.execute(idempotencyKey, IdempotencyOperation.CREATE_EVENT_V1, core, (trx) =>
      this.executeCore(core, trx),
    );
  }

  private async executeCore({event, users}: InputCore, trx: ITransaction): Promise<Output> {
    const [currency] = await this.rDataService.currency.findById(event.currencyId, trx);

    if (!this.supportedCurrencyService.isSupported(currency)) {
      return error(new CurrencyNotFoundError());
    }

    const eventValueObject = new EventValueObject(event);

    await this.rDataService.event.insert(eventValueObject.value, trx);

    const usersValue = users.map((u) => new UserInfoValueObject({...u, eventId: eventValueObject.value.id}).value);

    await this.rDataService.userInfo.insert(usersValue, trx);

    return success({...eventValueObject.value, users: usersValue});
  }
}
