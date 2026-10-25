import {error, success} from '#packages/result';

import {EventProtocolVersion} from '#domain/entities/event-protocol-version.enum';
import {ClientUpdateRequiredError} from '#domain/errors/errors';

import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {truncateAllTables} from '#test-support/db';
import {prepareInitRelationalState} from '#test-support/relational-state';

import {CheckEventProtocolUseCase} from '../check-event-protocol.usecase';

describe('CheckEventProtocolUseCase', () => {
  let relationalDataService: RelationalDataService;
  let useCase: CheckEventProtocolUseCase;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({dbConfig: appDbConfig, showQueryDetails: false});
    useCase = new CheckEventProtocolUseCase(relationalDataService);
    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
  });

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
            version: EventProtocolVersion.Legacy,
            createdAt: new Date('2026-10-01T00:00:00Z'),
            updatedAt: new Date('2026-10-01T00:00:00Z'),
            deletedAt: null,
          },
          {
            id: 'editing-event',
            name: 'Editing',
            currencyId: 'currency-1',
            pinCode: '1234',
            version: EventProtocolVersion.ExpenseEditing,
            createdAt: new Date('2026-10-01T00:00:00Z'),
            updatedAt: new Date('2026-10-01T00:00:00Z'),
            deletedAt: null,
          },
        ],
      },
    });

    await expect(useCase.execute({eventId: 'legacy-event', clientProtocolVersion: 1})).resolves.toEqual(success(true));
    await expect(useCase.execute({eventId: 'editing-event', clientProtocolVersion: 1})).resolves.toEqual(
      error(new ClientUpdateRequiredError(EventProtocolVersion.ExpenseEditing)),
    );
    await expect(useCase.execute({eventId: 'editing-event', clientProtocolVersion: 2})).resolves.toEqual(success(true));
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
            version: EventProtocolVersion.ExpenseEditing,
            createdAt: new Date('2026-10-01T00:00:00Z'),
            updatedAt: new Date('2026-10-01T00:00:00Z'),
            deletedAt: new Date('2026-10-02T00:00:00Z'),
          },
        ],
      },
    });

    await expect(
      useCase.execute({requestedProtocolVersion: EventProtocolVersion.ExpenseEditing, clientProtocolVersion: 1}),
    ).resolves.toEqual(error(new ClientUpdateRequiredError(EventProtocolVersion.ExpenseEditing)));
    await expect(useCase.execute({clientProtocolVersion: 1})).resolves.toEqual(success(true));
    await expect(useCase.execute({eventId: 'missing-event', clientProtocolVersion: 1})).resolves.toEqual(success(true));
    await expect(useCase.execute({eventId: 'deleted-event', clientProtocolVersion: 1})).resolves.toEqual(success(true));
  });
});
