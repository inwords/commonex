import {makeAutoObservable} from 'mobx';
import {Currency} from '@/5-entities/currency/types/types';

export class CurrencyStore {
  currencies: Array<Currency> = [];
  exchangeRate: Record<string, number> = {};

  constructor() {
    makeAutoObservable(this);
  }

  get currenciesOptions() {
    return this.currencies.map((c) => {
      return {id: c.id, label: c.code};
    });
  }

  getCurrencyCode(currencyId: string | undefined): string {
    if (!currencyId) {
      return '';
    }
    const currency = this.currencies.find((c) => c.id === currencyId);
    return currency?.code || '';
  }

  getCurrencyRate(currencyId: string): number | undefined {
    const currency = this.currencies.find((c) => c.id === currencyId);
    if (!currency) {
      return undefined;
    }
    return this.exchangeRate[currency.code];
  }

  calculateExchangeRate(fromCurrencyId: string, toCurrencyId: string): number {
    const fromRate = this.getCurrencyRate(fromCurrencyId);
    const toRate = this.getCurrencyRate(toCurrencyId);

    if (!fromRate || !toRate) {
      return 1; // fallback
    }

    return toRate / fromRate;
  }

  setCurrenciesWithRates(data: {currencies: Array<Currency>; exchangeRate: Record<string, number>}) {
    this.currencies = data.currencies;
    this.exchangeRate = data.exchangeRate;
  }
}

export const currencyStore = new CurrencyStore();
