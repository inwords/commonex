import {HttpService} from '@nestjs/axios';

import {CurrencyRateService} from '#frameworks/currency-rate-service/currency-rate-service';

import {env} from '../../../config';

describe('CurrencyRateService', () => {
  const get = jest.fn<Promise<{data: {rates?: Record<string, number>}}>, [string]>();
  const httpService = {axiosRef: {get}} as unknown as HttpService;
  const service = new CurrencyRateService(httpService, {retries: 3, minTimeout: 0, maxTimeout: 0});

  it('requests the historical endpoint for the date with the configured app id', async () => {
    get.mockResolvedValue({data: {rates: {USD: 1, EUR: 0.9}}});

    const rates = await service.getCurrencyRate('2026-01-06');

    expect(get).toHaveBeenCalledWith(
      `https://openexchangerates.org/api/historical/2026-01-06.json?app_id=${env.OPEN_EXCHANGE_RATES_API_ID}&base=USD`,
    );
    expect(rates).toEqual({USD: 1, EUR: 0.9});
  });

  it('returns null when the response has no rates', async () => {
    get.mockResolvedValue({data: {}});

    await expect(service.getCurrencyRate('2026-01-06')).resolves.toBeNull();
  });

  it('retries failed requests up to three times', async () => {
    get
      .mockRejectedValueOnce(new Error('503'))
      .mockRejectedValueOnce(new Error('503'))
      .mockResolvedValue({data: {rates: {USD: 1}}});

    await expect(service.getCurrencyRate('2026-01-06')).resolves.toEqual({USD: 1});
    expect(get).toHaveBeenCalledTimes(3);
  });

  it('throws the last error after the retries are exhausted', async () => {
    get.mockRejectedValue(new Error('down'));

    await expect(service.getCurrencyRate('2026-01-06')).rejects.toThrow('down');
    expect(get).toHaveBeenCalledTimes(4);
  });
});
