import {IBanner} from '#domain/entities/banner.entity';

import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {createTestRelationalDataService, truncateAllTables} from '#test-support/db';

describe('BannerRepository', () => {
  let dataService: RelationalDataService;
  const banner: IBanner = {
    id: 10,
    platform: 'Android',
    type: 'general_warning',
    text: 'Maintenance',
    minVersionCode: 100,
    maxVersionCode: 200,
    enabled: true,
  };

  beforeAll(async () => {
    dataService = createTestRelationalDataService({showQueryDetails: true});
    await dataService.initialize();
  });

  afterAll(async () => {
    await dataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(dataService.dataSource);
  });

  it('persists banner configuration and filters by enabled platform and version', async () => {
    const [, insertDetails] = await dataService.banner.insert(banner);
    const [found, details] = await dataService.banner.findAll({
      client: {platform: 'Android', versionCode: 150},
      enabled: true,
    });
    expect(found).toEqual([banner]);
    expect(insertDetails).toMatchSnapshot();
    expect(details).toMatchSnapshot();
  });

  it('lists all configuration in descending ID order', async () => {
    await dataService.banner.insert([banner, {...banner, id: 11, enabled: false}]);
    const [found, details] = await dataService.banner.findAll({});
    expect(found.map(({id}) => id)).toEqual([11, 10]);
    expect(details).toMatchSnapshot();
  });

  it('defaults direct SQL configuration to disabled with an unbounded version range', async () => {
    await dataService.dataSource.query('INSERT INTO banner (platform, type, text) VALUES ($1, $2, $3)', [
      'iOS',
      'general_warning',
      'Maintenance',
    ]);
    const [found] = await dataService.banner.findAll({});
    expect(found).toEqual([
      {...banner, id: 1, platform: 'iOS', minVersionCode: 0, maxVersionCode: null, enabled: false},
    ]);
  });

  it.each([
    ['platform', 'Web'],
    ['type', 'unknown'],
    ['text', ' \n\t'],
    ['min_version_code', -1],
    ['max_version_code', 99],
  ])('rejects invalid direct SQL configuration for %s', async (column, value) => {
    await dataService.banner.insert(banner);
    await expect(
      dataService.dataSource.query(`UPDATE banner SET ${column} = $1 WHERE id = $2`, [value, banner.id]),
    ).rejects.toMatchObject({
      driverError: {code: '23514'},
    });
  });
});
