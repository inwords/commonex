import {Module, OnApplicationShutdown, Provider} from '@nestjs/common';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {RelationalDataService} from './relational-data-service/postgres/relational-data-service';
import {appDbConfig} from './relational-data-service/postgres/config';
import {CurrencyRateServiceAbstract} from '#domain/abstracts/currency-rate-service/currency-rate-service';
import {HttpService} from '@nestjs/axios';
import {CurrencyRateService} from '#frameworks/currency-rate-service/currency-rate-service';
import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {EventService} from '#frameworks/event-service/event-service';
import {SupportedCurrencyServiceAbstract} from '#domain/abstracts/supported-currency-service/supported-currency-service';
import {CurrencyValueObject} from '#domain/value-objects/currency.value-object';
import {SupportedCurrencyService} from '#frameworks/supported-currency-service/supported-currency-service';
import {CURRENCIES_LIST} from '../constants';

export const providers: Provider[] = [
  {
    provide: RelationalDataServiceAbstract,
    useFactory: async (): Promise<RelationalDataService> => {
      const relationalDataService = new RelationalDataService({
        showQueryDetails: false,
        dbConfig: appDbConfig,
      });

      await relationalDataService.initialize();
      await initOrUpdateCurrencies(relationalDataService);

      return relationalDataService;
    },
  },
  {
    provide: CurrencyRateServiceAbstract,
    useFactory: (): CurrencyRateService => {
      return new CurrencyRateService(new HttpService());
    },
  },
  {
    provide: EventServiceAbstract,
    useClass: EventService,
  },
  {
    provide: SupportedCurrencyServiceAbstract,
    useFactory: (relationalDataService: RelationalDataServiceAbstract): SupportedCurrencyService => {
      return new SupportedCurrencyService(relationalDataService);
    },
    inject: [RelationalDataServiceAbstract],
  },
];

@Module({
  imports: [],
  providers: [...providers],
  exports: [...providers],
})
export class FrameworksLayer implements OnApplicationShutdown {
  constructor(private readonly relationalDataService: RelationalDataServiceAbstract) {}

  async onApplicationShutdown(): Promise<void> {
    await this.relationalDataService.destroy();
  }
}

const initOrUpdateCurrencies = async (rDataService: RelationalDataServiceAbstract): Promise<void> => {
  await rDataService.transaction('REPEATABLE READ', async (ctx) => {
    const [currencies] = await rDataService.currency.findAllSupported(undefined, {ctx});

    if (!currencies.length) {
      const currencies = CURRENCIES_LIST.map((currency) => new CurrencyValueObject(currency).value);

      await rDataService.currency.insert(currencies, {ctx});
    } else if (currencies.length !== CURRENCIES_LIST.length) {
      const existingCodes = new Set(currencies.map((c) => c.code));
      const missingCurrencies = CURRENCIES_LIST.filter((c) => !existingCodes.has(c.code)).map((c) => new CurrencyValueObject(c).value);

      if (missingCurrencies.length > 0) {
        await rDataService.currency.insert(missingCurrencies, {ctx});
      }
    }
  });
};
