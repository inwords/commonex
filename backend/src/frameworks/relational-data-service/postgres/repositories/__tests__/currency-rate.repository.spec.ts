import {CurrencyCode} from '#domain/entities/currency.entity';

import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {truncateAllTables} from '#test-support/db';

describe('CurrencyRateRepository', () => {
  let relationalDataService: RelationalDataService;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: true,
    });
    await relationalDataService.initialize();
    // Clear the database before running the tests
    await truncateAllTables(relationalDataService.dataSource);
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
  });

  describe('insert', () => {
    it('should insert currency rate correctly', async () => {
      const currencyRate = {
        date: '2023-01-01',
        rate: {
          EUR: 1,
          USD: 1.05,
          RUB: 80.5,
        },
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
      };

      const [, queryDetails] = await relationalDataService.currencyRate.insert(currencyRate);
      const [result] = await relationalDataService.currencyRate.findByDate('2023-01-01');

      expect(result).toMatchObject(currencyRate);

      expect(queryDetails).toMatchSnapshot();
    });

    it('should upsert currency rate by date', async () => {
      const initialCurrencyRate = {
        date: '2023-01-01',
        rate: {
          EUR: 1,
          USD: 1.05,
          RUB: 80.5,
        },
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
      };
      const updatedCurrencyRate = {
        date: '2023-01-01',
        rate: {
          EUR: 1,
          USD: 1.2,
          RUB: 90.5,
        },
        createdAt: new Date('2023-01-02T00:00:00Z'),
        updatedAt: new Date('2023-01-02T00:00:00Z'),
      };

      await relationalDataService.currencyRate.insert(initialCurrencyRate);
      await relationalDataService.currencyRate.insert(updatedCurrencyRate);

      const [result] = await relationalDataService.currencyRate.findByDate('2023-01-01');

      expect(result).toEqual(
        expect.objectContaining({
          date: '2023-01-01',
          rate: expect.objectContaining(updatedCurrencyRate.rate),
          createdAt: initialCurrencyRate.createdAt,
          updatedAt: updatedCurrencyRate.updatedAt,
        }),
      );
    });
  });

  describe('findByDate', () => {
    it('should find currency rate by date', async () => {
      // First insert test data
      const currencyRate = {
        date: '2023-01-01',
        rate: {
          EUR: 1,
          USD: 1.05,
          RUB: 80.5,
        },
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
      };

      await relationalDataService.currencyRate.insert(currencyRate);

      // Now look up that data
      const [result, queryDetails] = await relationalDataService.currencyRate.findByDate('2023-01-01');

      expect(result).toMatchObject(currencyRate);
      expect(queryDetails).toMatchSnapshot();
    });

    it('should return null for non-existent date', async () => {
      const [result, queryDetails] = await relationalDataService.currencyRate.findByDate('2023-12-31');

      expect(result).toBeNull();
      expect(queryDetails).toMatchSnapshot();
    });
  });

  describe('findAll', () => {
    it('should find all currency rates with limit', async () => {
      const currencyRates = [
        {
          date: '2023-01-01',
          rate: {
            EUR: 1,
            USD: 1.05,
            RUB: 80.5,
          },
          createdAt: new Date('2023-01-01T00:00:00Z'),
          updatedAt: new Date('2023-01-01T00:00:00Z'),
        },
        {
          date: '2023-01-02',
          rate: {
            EUR: 1,
            USD: 1.06,
            RUB: 81.0,
          },
          createdAt: new Date('2023-01-02T00:00:00Z'),
          updatedAt: new Date('2023-01-02T00:00:00Z'),
        },
      ];

      await relationalDataService.currencyRate.insert(currencyRates);

      const [result, queryDetails] = await relationalDataService.currencyRate.findAll({limit: 10});

      expect(result).toMatchObject(currencyRates);
      expect(queryDetails).toMatchSnapshot();
    });

    it('should respect limit parameter', async () => {
      const currencyRates = [
        {
          date: '2023-01-01',
          rate: {
            EUR: 1,
            USD: 1.05,
            RUB: 80.5,
          },
          createdAt: new Date('2023-01-01T00:00:00Z'),
          updatedAt: new Date('2023-01-01T00:00:00Z'),
        },
        {
          date: '2023-01-02',
          rate: {
            EUR: 1,
            USD: 1.06,
            RUB: 81.0,
          },
          createdAt: new Date('2023-01-02T00:00:00Z'),
          updatedAt: new Date('2023-01-02T00:00:00Z'),
        },
        {
          date: '2023-01-03',
          rate: {
            EUR: 1,
            USD: 1.07,
            RUB: 81.5,
          },
          createdAt: new Date('2023-01-03T00:00:00Z'),
          updatedAt: new Date('2023-01-03T00:00:00Z'),
        },
      ];

      await relationalDataService.currencyRate.insert(currencyRates);

      const [result, queryDetails] = await relationalDataService.currencyRate.findAll({limit: 2});

      expect(result).toHaveLength(2);
      expect(queryDetails).toMatchSnapshot();
    });
  });

  describe('findSupportedCurrenciesWithRatesVersionByDate', () => {
    it('returns the rate timestamp and supported currency timestamps ordered by code', async () => {
      await relationalDataService.currency.insert([
        {
          id: 'c-usd',
          code: CurrencyCode.USD,
          createdAt: new Date('2023-01-02T00:00:00Z'),
          updatedAt: new Date('2023-01-02T00:00:00Z'),
        },
        {
          id: 'c-eur',
          code: CurrencyCode.EUR,
          createdAt: new Date('2023-01-01T00:00:00Z'),
          updatedAt: new Date('2023-01-01T00:00:00Z'),
        },
        {
          id: 'c-xxx',
          code: 'XXX' as CurrencyCode,
          createdAt: new Date('2023-01-03T00:00:00Z'),
          updatedAt: new Date('2023-01-03T00:00:00Z'),
        },
      ]);
      await relationalDataService.currencyRate.insert({
        date: '2026-01-06',
        rate: {USD: 1, EUR: 0.9},
        createdAt: new Date('2026-01-06T12:00:00Z'),
        updatedAt: new Date('2026-01-06T12:00:00Z'),
      });

      const [version, details] = await relationalDataService.currencyRate.findSupportedCurrenciesWithRatesVersionByDate(
        '2026-01-06',
        undefined,
      );

      expect(version).toEqual({
        rateUpdatedAt: new Date('2026-01-06T12:00:00Z'),
        currenciesUpdatedAt: [new Date('2023-01-01T00:00:00Z'), new Date('2023-01-02T00:00:00Z')],
      });
      expect(details).toMatchSnapshot();
    });

    it('returns null when no rate exists for the date', async () => {
      const [version] = await relationalDataService.currencyRate.findSupportedCurrenciesWithRatesVersionByDate(
        '2026-01-06',
        undefined,
      );

      expect(version).toBeNull();
    });
  });
});
