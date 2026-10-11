import {CurrencyCode, ICurrency} from '#domain/entities/currency.entity';

import {SupportedCurrencyService} from '../supported-currency-service';

describe('SupportedCurrencyService', () => {
  const service = new SupportedCurrencyService();
  const usd: ICurrency = {
    id: 'usd',
    code: CurrencyCode.USD,
    createdAt: new Date('2026-01-01'),
    updatedAt: new Date('2026-01-01'),
  };
  const unsupported: ICurrency = {...usd, id: 'unsupported', code: 'ZZZ' as CurrencyCode};

  it('recognizes supported currencies and rejects missing or unsupported currencies', () => {
    expect(service.isSupported(usd)).toBe(true);
    expect(service.isSupported(unsupported)).toBe(false);
    expect(service.isSupported(null)).toBe(false);
    expect(service.supportedCodes).toContain(CurrencyCode.USD);
    expect(service.supportedCodes).not.toContain(unsupported.code);
  });

  it('filters currencies while preserving order and input records', () => {
    const eur = {...usd, id: 'eur', code: CurrencyCode.EUR};
    const input = Object.freeze([eur, unsupported, usd]);

    expect(service.filterCurrencies(input)).toEqual([eur, usd]);
    expect(input).toEqual([eur, unsupported, usd]);
  });

  it('filters rates without mutating the input or dropping zero rates', () => {
    const input = Object.freeze({USD: 1, EUR: 0, ZZZ: 999});

    expect(service.filterRates(input)).toEqual({USD: 1, EUR: 0});
    expect(input).toEqual({USD: 1, EUR: 0, ZZZ: 999});
  });

  it('returns empty collections when no supported data is provided', () => {
    expect(service.filterCurrencies([])).toEqual([]);
    expect(service.filterCurrencies([unsupported])).toEqual([]);
    expect(service.filterRates({})).toEqual({});
    expect(service.filterRates({ZZZ: 999})).toEqual({});
  });
});
