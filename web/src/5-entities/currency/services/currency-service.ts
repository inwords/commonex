import {getCurrenciesWithRates} from '@/5-entities/currency/services/api';
import {currencyStore} from '@/5-entities/currency/stores/currency-store';

export class CurrencyService {
  async fetchCurrencies() {
    const data = await getCurrenciesWithRates();

    currencyStore.setCurrenciesWithRates(data);
  }
}

export const currencyService = new CurrencyService();
