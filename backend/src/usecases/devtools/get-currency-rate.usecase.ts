import {Injectable} from '@nestjs/common';

import {UseCase} from '#packages/use-case';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {ICurrencyRate} from '#domain/entities/currency-rate.entity';

interface Input {
  date: string;
}
type Output = ICurrencyRate | null;

@Injectable()
export class GetCurrencyRateUseCase implements UseCase<Input, Output> {
  constructor(private readonly rDataService: RelationalDataServiceAbstract) {}

  public async execute({date}: Input): Promise<Output> {
    const [currencyRate] = await this.rDataService.currencyRate.findByDate(date);

    return currencyRate;
  }
}
