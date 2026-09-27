import {CleanupExpiredDataUseCase} from '#usecases/cron/cleanup-expired-data.usecase';

import {EventShareTokenEntity} from '#frameworks/relational-data-service/postgres/entities/event-share-token.entity';
import {IdempotencyKeyEntity} from '#frameworks/relational-data-service/postgres/entities/idempotency-key.entity';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {createTestRelationalDataService, truncateAllTables} from '#test-support/db';

const HOUR_MS = 60 * 60 * 1000;

describe('CleanupExpiredDataUseCase', () => {
  let relationalDataService: RelationalDataService;
  let useCase: CleanupExpiredDataUseCase;

  beforeAll(async () => {
    relationalDataService = createTestRelationalDataService();
    useCase = new CleanupExpiredDataUseCase(relationalDataService);
    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
  });

  it('cleans expired records in bounded batches under one job lock', async () => {
    const expiredKeys = Array.from({length: 501}, (_, index) => ({
      key: `expired-${index.toString().padStart(3, '0')}`,
      url: '/u',
      requestHash: 'h',
      response: {},
      statusCode: 200,
      expiresAt: new Date(Date.now() - HOUR_MS),
      createdAt: new Date(),
    }));
    const expiredTokens = Array.from({length: 501}, (_, index) => ({
      token: `expired-${index.toString().padStart(3, '0')}`,
      eventId: 'event-1',
      expiresAt: new Date('2025-01-01T00:00:00Z'),
      createdAt: new Date('2024-01-01T00:00:00Z'),
    }));

    await relationalDataService.dataSource.getRepository(IdempotencyKeyEntity).insert([
      ...expiredKeys,
      {
        key: 'live',
        url: '/u',
        requestHash: 'h',
        response: {},
        statusCode: 200,
        expiresAt: new Date(Date.now() + HOUR_MS),
        createdAt: new Date(),
      },
    ]);
    await relationalDataService.dataSource.getRepository(EventShareTokenEntity).insert([
      ...expiredTokens,
      {
        token: 'live',
        eventId: 'event-1',
        expiresAt: new Date('2099-01-01T00:00:00Z'),
        createdAt: new Date('2024-01-01T00:00:00Z'),
      },
    ]);

    await expect(useCase.execute()).resolves.toEqual({
      acquired: true,
      result: {
        idempotencyKeys: {deletedCount: 501, batchCount: 2},
        eventShareTokens: {deletedCount: 501, batchCount: 2},
      },
    });
    await expect(relationalDataService.dataSource.getRepository(IdempotencyKeyEntity).count()).resolves.toBe(1);
    await expect(relationalDataService.dataSource.getRepository(EventShareTokenEntity).count()).resolves.toBe(1);
  });

  it('skips cleanup when another transaction holds the job lock', async () => {
    await relationalDataService.dataSource.transaction(async (ctx) => {
      await ctx.query(`SELECT pg_advisory_xact_lock(hashtextextended($1, 0))`, ['commonex:cron:data-retention']);

      await expect(useCase.execute()).resolves.toEqual({acquired: false});
    });
  });
});
