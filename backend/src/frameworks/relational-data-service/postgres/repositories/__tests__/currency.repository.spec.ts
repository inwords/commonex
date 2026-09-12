import {CurrencyCode} from '#domain/entities/currency.entity';

import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {truncateAllTables} from '#test-support/db';

describe('CurrencyRepository', () => {
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
    it('should insert single currency correctly', async () => {
      const currency = {
        id: 'currency-1',
        code: CurrencyCode.EUR,
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
      };

      const [, queryDetails] = await relationalDataService.currency.insert(currency);
      const [result] = await relationalDataService.currency.findById('currency-1');

      expect(result).toMatchObject(currency);
      expect(queryDetails).toMatchSnapshot();
    });

    it('should insert multiple currencies correctly', async () => {
      const currencies = [
        {
          id: 'currency-1',
          code: CurrencyCode.EUR,
          createdAt: new Date('2023-01-01T00:00:00Z'),
          updatedAt: new Date('2023-01-01T00:00:00Z'),
        },
        {
          id: 'currency-2',
          code: CurrencyCode.USD,
          createdAt: new Date('2023-01-01T00:00:00Z'),
          updatedAt: new Date('2023-01-01T00:00:00Z'),
        },
      ];

      const [, queryDetails] = await relationalDataService.currency.insert(currencies);
      const [result] = await relationalDataService.currency.findAll({limit: 2});

      expect(result).toMatchObject(currencies);
      expect(queryDetails).toMatchSnapshot();
    });
  });

  describe('findById', () => {
    it('should find currency by id', async () => {
      // First insert test data
      const currency = {
        id: 'currency-1',
        code: CurrencyCode.EUR,
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
      };

      await relationalDataService.currency.insert(currency);

      // Now look up that data
      const [result, queryDetails] = await relationalDataService.currency.findById('currency-1');

      expect(result).toMatchObject(currency);
      expect(queryDetails).toMatchSnapshot();
    });

    it('should return null for non-existent currency', async () => {
      const [result, queryDetails] = await relationalDataService.currency.findById('non-existent');

      expect(result).toBeNull();
      expect(queryDetails).toMatchSnapshot();
    });
  });

  describe('findAll', () => {
    it('should find all currencies with limit', async () => {
      // First insert test data
      const currencies = [
        {
          id: 'currency-1',
          code: CurrencyCode.EUR,
          createdAt: new Date('2023-01-01T00:00:00Z'),
          updatedAt: new Date('2023-01-01T00:00:00Z'),
        },
      ];

      await relationalDataService.currency.insert(currencies);

      // Now fetch all currencies with a limit
      const [result, queryDetails] = await relationalDataService.currency.findAll({limit: 1});

      expect(result).toMatchObject(currencies);
      expect(queryDetails).toMatchSnapshot();
    });

    it('should return empty array when no currencies exist', async () => {
      const [result, queryDetails] = await relationalDataService.currency.findAll({limit: 1});

      expect(result).toEqual([]);
      expect(queryDetails).toMatchSnapshot();
    });
  });
});
