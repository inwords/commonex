import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {createTestRelationalDataService} from '#test-support/db';

describe('database schema', () => {
  let relationalDataService: RelationalDataService;

  beforeAll(async () => {
    relationalDataService = createTestRelationalDataService();
    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  it('has every migration applied', async () => {
    await expect(relationalDataService.dataSource.showMigrations()).resolves.toBe(false);
  });

  it('matches the entity metadata with no pending schema changes', async () => {
    const pending = await relationalDataService.dataSource.driver.createSchemaBuilder().log();

    expect(pending.upQueries.map((query) => query.query)).toEqual([]);
  });
});
