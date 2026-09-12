import {HttpService} from '@nestjs/axios';
import retry from 'async-retry';

import {CurrencyRateServiceAbstract} from '#domain/abstracts/currency-rate-service/currency-rate-service';
import {ICurrencyRate} from '#domain/entities/currency-rate.entity';

import {env} from '../../config';

export const DEFAULT_CURRENCY_RATE_RETRY_OPTIONS: retry.Options = {retries: 3};

export class CurrencyRateService implements CurrencyRateServiceAbstract {
  constructor(
    private readonly httpService: HttpService,
    private readonly retryOptions: retry.Options = DEFAULT_CURRENCY_RATE_RETRY_OPTIONS,
  ) {}

  getCurrencyRate: (date: ICurrencyRate['date']) => Promise<Record<string, number> | null> = async (date) => {
    const result = await retry(
      async () =>
        this.httpService.axiosRef.get<{rates?: Record<string, number>}>(
          `https://openexchangerates.org/api/historical/${date}.json?app_id=${env.OPEN_EXCHANGE_RATES_API_ID}&base=USD`,
        ),
      this.retryOptions,
    );

    const rate = result.data.rates;

    return rate ?? null;
  };
}
