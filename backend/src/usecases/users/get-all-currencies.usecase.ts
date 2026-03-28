import {UseCase} from '#packages/use-case';
import {Injectable} from '@nestjs/common';
import {ICurrency} from '#domain/entities/currency.entity';
import {SupportedCurrencyServiceAbstract} from '#domain/abstracts/supported-currency-service/supported-currency-service';
import {Result, success} from '#packages/result';

@Injectable()
export class GetAllCurrenciesUseCase implements UseCase<void, Result<Array<ICurrency>, never>> {
  constructor(private readonly supportedCurrencyService: SupportedCurrencyServiceAbstract) {}

  public async execute(): Promise<Result<Array<ICurrency>, never>> {
    const currencies = await this.supportedCurrencyService.findAll();

    return success(currencies);
  }
}
