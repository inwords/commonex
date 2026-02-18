import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';

describe('CurrencyRateRepository', () => {
  let relationalDataService: RelationalDataService;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: true,
    });
    await relationalDataService.initialize();
    // Очищаем базу данных перед запуском тестов
    await relationalDataService.flush();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await relationalDataService.flush();
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
      // Сначала вставляем тестовые данные
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

      // Теперь ищем эти данные
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
});
