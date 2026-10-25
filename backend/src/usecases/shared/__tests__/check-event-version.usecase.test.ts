import {error, success} from '#packages/result';

import {EventVersion} from '#domain/entities/event-version.enum';
import {ClientUpdateRequiredError} from '#domain/errors/errors';

import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {truncateAllTables} from '#test-support/db';
import {prepareInitRelationalState} from '#test-support/relational-state';

import {CheckEventVersionSharedUseCase} from '../check-event-version.usecase';

describe('CheckEventVersionSharedUseCase', () => {
  let relationalDataService: RelationalDataService;
  let useCase: CheckEventVersionSharedUseCase;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({dbConfig: appDbConfig, showQueryDetails: false});
    useCase = new CheckEventVersionSharedUseCase(relationalDataService);
    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
  });

  it.each([undefined, '', '0', '-1', 'invalid', '2.0', '9007199254740992'])(
    'requires an update for a mobile client with unsupported version %s',
    async (clientVersion) => {
      await expect(
        useCase.execute({
          userAgent: 'CommonEx/1 (Android/r)',
          clientVersion,
          requestedVersion: EventVersion.ExpenseEditing,
        }),
      ).resolves.toEqual(error(new ClientUpdateRequiredError(EventVersion.ExpenseEditing)));
    },
  );

  it.each([undefined, 'Mozilla/5.0 (Linux; Android 14)', 'other-client'])(
    'allows a non-mobile caller with user agent %s',
    async (userAgent) => {
      await expect(useCase.execute({userAgent, requestedVersion: EventVersion.ExpenseEditing})).resolves.toEqual(
        success(true),
      );
    },
  );

  it.each(['CommonEx/1 (iOS/r)', 'grpc-node-js CommonEx/1 (Android/d)'])(
    'checks compatibility for mobile user agent %s',
    async (userAgent) => {
      await expect(useCase.execute({userAgent, requestedVersion: EventVersion.ExpenseEditing})).resolves.toEqual(
        error(new ClientUpdateRequiredError(EventVersion.ExpenseEditing)),
      );
      await expect(
        useCase.execute({userAgent, clientVersion: '3', requestedVersion: EventVersion.ExpenseEditing}),
      ).resolves.toEqual(success(true));
    },
  );

  it('allows legacy events for legacy clients and requires an update for expense editing events', async () => {
    await prepareInitRelationalState({
      rDataService: relationalDataService,
      initState: {
        events: [
          {
            id: 'legacy-event',
            name: 'Legacy',
            currencyId: 'currency-1',
            pinCode: '1234',
            version: EventVersion.Legacy,
            createdAt: new Date('2026-10-01T00:00:00Z'),
            updatedAt: new Date('2026-10-01T00:00:00Z'),
            deletedAt: null,
          },
          {
            id: 'editing-event',
            name: 'Editing',
            currencyId: 'currency-1',
            pinCode: '1234',
            version: EventVersion.ExpenseEditing,
            createdAt: new Date('2026-10-01T00:00:00Z'),
            updatedAt: new Date('2026-10-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
      },
    });

    await expect(
      useCase.execute({eventId: 'legacy-event', userAgent: 'CommonEx/1 (Android/r)', clientVersion: '1'}),
    ).resolves.toEqual(success(true));
    await expect(
      useCase.execute({eventId: 'editing-event', userAgent: 'CommonEx/1 (Android/r)', clientVersion: '1'}),
    ).resolves.toEqual(error(new ClientUpdateRequiredError(EventVersion.ExpenseEditing)));
    await expect(
      useCase.execute({eventId: 'editing-event', userAgent: 'CommonEx/1 (Android/r)', clientVersion: '2'}),
    ).resolves.toEqual(success(true));
  });

  it('checks requested creation protocol versions and treats missing or deleted events as compatible', async () => {
    await prepareInitRelationalState({
      rDataService: relationalDataService,
      initState: {
        events: [
          {
            id: 'deleted-event',
            name: 'Deleted',
            currencyId: 'currency-1',
            pinCode: '1234',
            version: EventVersion.ExpenseEditing,
            createdAt: new Date('2026-10-01T00:00:00Z'),
            updatedAt: new Date('2026-10-01T00:00:00Z'),
            deletedAt: new Date('2026-10-02T00:00:00Z'),
          },
        ],
      },
    });

    await expect(
      useCase.execute({
        requestedVersion: EventVersion.ExpenseEditing,
        userAgent: 'CommonEx/1 (Android/r)',
        clientVersion: '1',
      }),
    ).resolves.toEqual(error(new ClientUpdateRequiredError(EventVersion.ExpenseEditing)));
    await expect(useCase.execute({userAgent: 'CommonEx/1 (Android/r)', clientVersion: '1'})).resolves.toEqual(
      success(true),
    );
    await expect(
      useCase.execute({eventId: 'missing-event', userAgent: 'CommonEx/1 (Android/r)', clientVersion: '1'}),
    ).resolves.toEqual(success(true));
    await expect(
      useCase.execute({eventId: 'deleted-event', userAgent: 'CommonEx/1 (Android/r)', clientVersion: '1'}),
    ).resolves.toEqual(success(true));
  });
});
