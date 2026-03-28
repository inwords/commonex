import {UseCase} from '#packages/use-case';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {SupportedCurrencyServiceAbstract} from '#domain/abstracts/supported-currency-service/supported-currency-service';
import {EventValueObject} from '#domain/value-objects/event.value-object';
import {IUserInfo} from '#domain/entities/user-info.entity';
import {IEvent} from '#domain/entities/event.entity';
import {UserInfoValueObject} from '#domain/value-objects/user-info.value-object';
import {Injectable} from '@nestjs/common';
import {Result, success, error} from '#packages/result';
import {CurrencyNotFoundError} from '#domain/errors/errors';

type Input = {users: Array<Omit<IUserInfo, 'id' | 'eventId'>>; event: Pick<IEvent, 'name' | 'currencyId' | 'pinCode'>};
type Output = Result<IEvent & {users: IUserInfo[]}, CurrencyNotFoundError>;

@Injectable()
export class SaveEventUseCase implements UseCase<Input, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly supportedCurrencyService: SupportedCurrencyServiceAbstract,
  ) {}

  public async execute({event, users}: Input): Promise<Output> {
    return await this.rDataService.transaction(async (ctx) => {
      const currency = await this.supportedCurrencyService.findById(event.currencyId, {ctx});

      if (!currency) {
        return error(new CurrencyNotFoundError());
      }

      const eventValueObject = new EventValueObject(event);

      await this.rDataService.event.insert(eventValueObject.value, {ctx});

      const usersValue = users.map((u) => new UserInfoValueObject({...u, eventId: eventValueObject.value.id}).value);

      await this.rDataService.userInfo.insert(usersValue, {ctx});

      return success({...eventValueObject.value, users: usersValue});
    });
  }
}
