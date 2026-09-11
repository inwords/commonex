import {ICurrencyRate} from '#domain/entities/currency-rate.entity';
import {CurrencyRateServiceAbstract} from '#domain/abstracts/currency-rate-service/currency-rate-service';
import {HttpService} from '@nestjs/axios';
import retry from 'async-retry';
import {env} from '../../config';

export class CurrencyRateService implements CurrencyRateServiceAbstract {
  constructor(private readonly httpService: HttpService) {}

  getCurrencyRate: (date: ICurrencyRate['date']) => Promise<Record<string, number> | null> = async (date) => {
    const result = await retry(
      async () =>
        this.httpService.axiosRef.get<{rates?: Record<string, number>}>(
          `https://openexchangerates.org/api/historical/${date}.json?app_id=${env.OPEN_EXCHANGE_RATES_API_ID}&base=USD`,
        ),
      {retries: 3},
    );

    const rate = result.data.rates;

    return rate ?? null;
  };
}
