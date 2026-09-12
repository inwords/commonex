import {LessThan} from 'typeorm';

import {IIdempotencyKey} from '#domain/entities/idempotency-key.entity';

import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {createTestRelationalDataService, truncateAllTables} from '#test-support/db';

const buildKey = (overrides: Partial<IIdempotencyKey> = {}): IIdempotencyKey => ({
  key: 'key-1',
  url: '/user/event',
  requestHash: 'hash-1',
  response: {id: 'event-1'},
  statusCode: 200,
  expiresAt: new Date('2026-01-02T00:00:00Z'),
  createdAt: new Date('2026-01-01T00:00:00Z'),
  ...overrides,
});

describe('IdempotencyKeyRepository', () => {
  let relationalDataService: RelationalDataService;

  beforeAll(async () => {
    relationalDataService = createTestRelationalDataService({showQueryDetails: true});
    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
  });

  it('inserts a key and finds it by key', async () => {
    const record = buildKey();

    const [, insertDetails] = await relationalDataService.idempotencyKey.insert(record);
    const [found, findDetails] = await relationalDataService.idempotencyKey.findByKey('key-1');

    expect(found).toMatchObject(record);
    expect(insertDetails).toMatchSnapshot();
    expect(findDetails).toMatchSnapshot();
  });

  it('returns null for an unknown key', async () => {
    const [found] = await relationalDataService.idempotencyKey.findByKey('missing');

    expect(found).toBeNull();
  });

  it('lists keys up to the limit', async () => {
    for (const key of ['a', 'b', 'c']) {
      await relationalDataService.idempotencyKey.insert(buildKey({key}));
    }

    const [found, details] = await relationalDataService.idempotencyKey.findAll({limit: 2});

    expect(found).toHaveLength(2);
    expect(details).toMatchSnapshot();
  });

  it('deletes only the keys matching the criteria', async () => {
    await relationalDataService.idempotencyKey.insert(
      buildKey({key: 'expired', expiresAt: new Date('2025-12-31T00:00:00Z')}),
    );
    await relationalDataService.idempotencyKey.insert(
      buildKey({key: 'valid', expiresAt: new Date('2026-12-31T00:00:00Z')}),
    );

    const [, details] = await relationalDataService.idempotencyKey.delete({
      expiresAt: LessThan(new Date('2026-01-01T00:00:00Z')),
    });
    const [remaining] = await relationalDataService.idempotencyKey.findAll({limit: 10});

    expect(remaining.map((record) => record.key)).toEqual(['valid']);
    expect(details).toMatchSnapshot();
  });

  it('rejects empty criteria instead of deleting every key', async () => {
    await relationalDataService.idempotencyKey.insert(buildKey({key: 'kept'}));

    await expect(relationalDataService.idempotencyKey.delete({})).rejects.toThrow('Empty criteria');
    const [remaining] = await relationalDataService.idempotencyKey.findAll({limit: 10});

    expect(remaining.map((record) => record.key)).toEqual(['kept']);
  });
});
