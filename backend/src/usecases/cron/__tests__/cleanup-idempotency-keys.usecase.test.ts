import {CleanupIdempotencyKeysUseCase} from '#usecases/cron/cleanup-idempotency-keys.usecase';

import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {createTestRelationalDataService, truncateAllTables} from '#test-support/db';
import {TestCase, prepareInitRelationalState, validateRelationalStateChanges} from '#test-support/relational-state';

type CleanupIdempotencyKeysTestCase = TestCase<CleanupIdempotencyKeysUseCase>;

const HOUR_MS = 60 * 60 * 1000;
const baseKey = {url: '/u', requestHash: 'h', response: {}, statusCode: 200, createdAt: new Date()};

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

  const testCases: CleanupIdempotencyKeysTestCase[] = [
    {
      name: 'deletes expired keys and keeps live ones',
      initRelationalState: {
        idempotencyKeys: [
          {...baseKey, key: 'expired', expiresAt: new Date(Date.now() - HOUR_MS)},
          {...baseKey, key: 'live', expiresAt: new Date(Date.now() + HOUR_MS)},
        ],
      },
      input: undefined,
      output: undefined,
      relationalStateChanges: {idempotencyKeys: {deleted: [{where: {key: 'expired'}}]}},
    },
    {
      name: 'changes nothing when no key has expired',
      initRelationalState: {
        idempotencyKeys: [{...baseKey, key: 'live', expiresAt: new Date(Date.now() + HOUR_MS)}],
      },
      input: undefined,
      output: undefined,
      relationalStateChanges: {},
    },
  ];

  testCases.forEach((testCase) => {
    it(testCase.name, async () => {
      await prepareInitRelationalState({rDataService: relationalDataService, initState: testCase.initRelationalState});

      await expect(useCase.execute()).resolves.toEqual(testCase.output);

      await validateRelationalStateChanges({
        rDataService: relationalDataService,
        initState: testCase.initRelationalState,
        stateChanges: testCase.relationalStateChanges ?? {},
      });
    });
  });
});
