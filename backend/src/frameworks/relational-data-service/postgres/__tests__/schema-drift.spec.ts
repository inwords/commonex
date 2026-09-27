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

  it('declares the measured lookup and retention indexes in entity metadata', () => {
    const indexColumns = Object.fromEntries(
      relationalDataService.dataSource.entityMetadatas.flatMap((metadata) =>
        metadata.indices.map((index) => [index.name, index.columns.map((column) => column.databaseName)]),
      ),
    );

    expect(indexColumns).toMatchObject({
      idx__expense__event_id: ['event_id'],
      idx__user_info__event_id: ['event_id'],
      idx__event_share_token__event_id__expires_at: ['event_id', 'expires_at'],
      idx__event_share_token__expires_at: ['expires_at'],
    });
    expect(indexColumns).not.toHaveProperty('idx__event_share_token__event_id');
  });
});
