import {UseCase} from '#packages/use-case';
import {Injectable} from '@nestjs/common';
import {ICurrency} from '#domain/entities/currency.entity';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {CURRENCIES_LIST} from '../../constants';
import {error, Result, success} from '#packages/result';
import {getCurrentDateWithoutTimeUTC} from '#packages/date-utils';
import {CurrencyNotFoundError} from '#domain/errors';

type Output = Result<
  {
    currencies: ICurrency[];
    exchangeRate: Record<string, number>;
  },
  CurrencyNotFoundError
>;

@Injectable()
export class GetAllCurrenciesWithRatesUseCase implements UseCase<void, Output> {
  constructor(private readonly rDataService: RelationalDataServiceAbstract) {}

  public async execute(): Promise<Output> {
    // Получаем все валюты
    const [currencies] = await this.rDataService.currency.findAll({limit: CURRENCIES_LIST.length});

    // Получаем текущие курсы
    const currentDate = getCurrentDateWithoutTimeUTC();
    const [currencyRate] = await this.rDataService.currencyRate.findByDate(currentDate);

    if (!currencyRate) {
      return error(new CurrencyNotFoundError());
    }

    return success({
      currencies,
      exchangeRate: currencyRate.rate,
    });
  }
}
