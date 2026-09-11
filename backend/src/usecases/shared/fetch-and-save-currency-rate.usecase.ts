import {Injectable} from '@nestjs/common';

import {UseCase} from '#packages/use-case';

import {CurrencyRateServiceAbstract} from '#domain/abstracts/currency-rate-service/currency-rate-service';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {ICurrencyRate} from '#domain/entities/currency-rate.entity';
import {CurrencyRateValueObject} from '#domain/value-objects/currency-rate.value-object';

type Input = {
  date: string;
};
type Output = ICurrencyRate;

@Injectable()
export class FetchAndSaveCurrencyRateSharedUseCase implements UseCase<Input, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly currencyRateService: CurrencyRateServiceAbstract,
  ) {}

  public async execute({date}: Input): Promise<Output> {
    const rates = await this.currencyRateService.getCurrencyRate(date);

    if (!rates) {
      throw new Error(`Failed to fetch currency rates for ${date}`);
    }

    const currencyRate = new CurrencyRateValueObject({
      date,
      rate: rates,
    }).value;

    await this.rDataService.currencyRate.insert(currencyRate);

    return currencyRate;
  }
}
