import {CurrencyCode} from '#domain/entities/currency.entity';

import {initOrUpdateCurrencies} from '#frameworks/frameworks.layer';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {createTestRelationalDataService, truncateAllTables} from '#test-support/db';

import {CURRENCIES_LIST} from '../../constants';

describe('initOrUpdateCurrencies', () => {
  let relationalDataService: RelationalDataService;
  const expectedCodes = CURRENCIES_LIST.map(({code}) => code).sort();

  beforeAll(async () => {
    relationalDataService = createTestRelationalDataService();
    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
  });

  it('seeds every supported currency into an empty table', async () => {
    await initOrUpdateCurrencies(relationalDataService);

    const [currencies] = await relationalDataService.currency.findAllSupported({orderBy: 'code'});

    expect(currencies.map(({code}) => code)).toEqual(expectedCodes);
  });

  it('backfills missing currencies without touching existing rows', async () => {
    await relationalDataService.currency.insert({
      id: 'existing-usd',
      code: CurrencyCode.USD,
      createdAt: new Date('2020-01-01T00:00:00Z'),
      updatedAt: new Date('2020-01-01T00:00:00Z'),
    });

    await initOrUpdateCurrencies(relationalDataService);

    const [currencies] = await relationalDataService.currency.findAllSupported({orderBy: 'code'});
    const usd = currencies.find(({code}) => code === CurrencyCode.USD);

    expect(currencies.map(({code}) => code)).toEqual(expectedCodes);
    expect(usd).toMatchObject({id: 'existing-usd'});
  });

  it('changes nothing when every currency exists', async () => {
    await initOrUpdateCurrencies(relationalDataService);
    const [before] = await relationalDataService.currency.findAllSupported({orderBy: 'code'});

    await initOrUpdateCurrencies(relationalDataService);
    const [after] = await relationalDataService.currency.findAllSupported({orderBy: 'code'});

    expect(after).toEqual(before);
  });
});
