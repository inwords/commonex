import {CleanupIdempotencyKeysUseCase} from '#usecases/cron/cleanup-idempotency-keys.usecase';

import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {createTestRelationalDataService, truncateAllTables} from '#test-support/db';
import {prepareInitRelationalState} from '#test-support/relational-state';

describe('CleanupIdempotencyKeysUseCase', () => {
  let relationalDataService: RelationalDataService;
  let useCase: CleanupIdempotencyKeysUseCase;

  beforeAll(async () => {
    relationalDataService = createTestRelationalDataService();
    useCase = new CleanupIdempotencyKeysUseCase(relationalDataService);
    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
  });

  it('deletes expired keys and keeps live ones', async () => {
    const hour = 60 * 60 * 1000;
    const base = {url: '/u', requestHash: 'h', response: {}, statusCode: 200, createdAt: new Date()};
    await prepareInitRelationalState({
      rDataService: relationalDataService,
      initState: {
        idempotencyKeys: [
          {...base, key: 'expired', expiresAt: new Date(Date.now() - hour)},
          {...base, key: 'live', expiresAt: new Date(Date.now() + hour)},
        ],
      },
    });

    await useCase.execute();
    const [remaining] = await relationalDataService.idempotencyKey.findAll({limit: 10});

    expect(remaining.map((record) => record.key)).toEqual(['live']);
  });
});
