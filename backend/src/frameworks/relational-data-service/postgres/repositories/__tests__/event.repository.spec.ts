import {QueryFailedError} from 'typeorm';

import {IQueryDetails, ITransaction} from '#domain/abstracts/relational-data-service/types';
import {IEvent} from '#domain/entities/event.entity';

import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {
  createTransactionHandle,
  invalidateTransactionHandle,
} from '#frameworks/relational-data-service/postgres/transaction-context';

import {truncateAllTables} from '#test-support/db';

describe('EventRepository', () => {
  let relationalDataService: RelationalDataService;

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: true,
    });
    await relationalDataService.initialize();
    // Clear the database before running the tests
    await truncateAllTables(relationalDataService.dataSource);
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
  });

  describe('insert', () => {
    it('should insert event correctly', async () => {
      const event = {
        id: 'event-1',
        name: 'Test Event',
        currencyId: 'currency-1',
        pinCode: '1234',
        version: 1,
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
        deletedAt: null,
      };

      const [, queryDetails] = await relationalDataService.event.insert(event);
      const [insertedEvent] = await relationalDataService.event.findById('event-1');

      expect(insertedEvent).toMatchObject(event);
      expect(queryDetails).toMatchSnapshot();
    });
  });

  describe('findById', () => {
    it('should find event by id', async () => {
      // First insert test data
      const event = {
        id: 'event-1',
        name: 'Test Event',
        currencyId: 'currency-1',
        pinCode: '1234',
        version: 1,
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
        deletedAt: null,
      };

      await relationalDataService.event.insert(event);

      // Now look up that data
      const [result, queryDetails] = await relationalDataService.event.findById('event-1');

      expect(result).toMatchObject(event);
      expect(queryDetails).toMatchSnapshot();
    });

    it('should return null for non-existent event', async () => {
      const [result, queryDetails] = await relationalDataService.event.findById('non-existent');

      expect(result).toBeNull();
      expect(queryDetails).toMatchSnapshot();
    });

    it('rejects invalid transaction contexts before reading or writing event rows', async () => {
      const event = {
        id: 'event-1',
        name: 'Test Event',
        currencyId: 'currency-1',
        pinCode: '1234',
        version: 1,
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
        deletedAt: null,
      };
      await relationalDataService.event.insert(event);
      const invalidTransaction = {ctx: {}} as ITransaction;

      await expect(relationalDataService.event.findById('event-1', invalidTransaction)).rejects.toBeInstanceOf(
        TypeError,
      );
      await expect(
        relationalDataService.event.findById('event-1', {...invalidTransaction, lock: 'pessimistic_write'}),
      ).rejects.toBeInstanceOf(TypeError);
      await expect(
        relationalDataService.event.update(
          'event-1',
          {deletedAt: new Date('2023-01-02T00:00:00Z')},
          invalidTransaction,
        ),
      ).rejects.toBeInstanceOf(TypeError);

      const [unchanged] = await relationalDataService.event.findById('event-1');
      expect(unchanged).toMatchObject(event);
    });

    it('should find event by id with pessimistic_write lock', async () => {
      // First insert test data
      const event = {
        id: 'event-1',
        name: 'Test Event',
        currencyId: 'currency-1',
        pinCode: '1234',
        version: 1,
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
        deletedAt: null,
      };

      await relationalDataService.event.insert(event);

      // Look it up with a lock inside a transaction
      const result: {result: IEvent | null; queryDetails: IQueryDetails} = await relationalDataService.transaction(
        async (trx) => {
          const [foundEvent, queryDetails] = await relationalDataService.event.findById('event-1', {
            ...trx,
            lock: 'pessimistic_write',
          });

          return {result: foundEvent, queryDetails};
        },
      );

      expect(result.result).toMatchObject(event);
      expect(result.queryDetails).toMatchSnapshot();
    });

    it('should preserve query failures when an event row cannot be locked immediately', async () => {
      const event = {
        id: 'event-1',
        name: 'Test Event',
        currencyId: 'currency-1',
        pinCode: '1234',
        version: 1,
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
        deletedAt: null,
      };

      await relationalDataService.event.insert(event);

      let notifyLockAcquired!: () => void;
      const lockAcquired = new Promise<void>((resolve) => {
        notifyLockAcquired = resolve;
      });
      let releaseLock!: () => void;
      const lockRelease = new Promise<void>((resolve) => {
        releaseLock = resolve;
      });

      const lockHolder = relationalDataService.transaction(async (trx) => {
        await relationalDataService.event.findById('event-1', {...trx, lock: 'pessimistic_write'});
        notifyLockAcquired();
        await lockRelease;
      });

      await lockAcquired;

      try {
        await expect(
          relationalDataService.transaction((trx) =>
            relationalDataService.event.findById('event-1', {
              ...trx,
              lock: 'pessimistic_write',
              onLocked: 'nowait',
            }),
          ),
        ).rejects.toBeInstanceOf(QueryFailedError);
      } finally {
        releaseLock();
        await lockHolder;
      }
    });

    it('should preserve lock timeout errors when nowait was not requested', async () => {
      const event = {
        id: 'event-1',
        name: 'Test Event',
        currencyId: 'currency-1',
        pinCode: '1234',
        version: 1,
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
        deletedAt: null,
      };

      await relationalDataService.event.insert(event);

      let notifyLockAcquired!: () => void;
      const lockAcquired = new Promise<void>((resolve) => {
        notifyLockAcquired = resolve;
      });
      let releaseLock!: () => void;
      const lockRelease = new Promise<void>((resolve) => {
        releaseLock = resolve;
      });

      const lockHolder = relationalDataService.transaction(async (trx) => {
        await relationalDataService.event.findById('event-1', {...trx, lock: 'pessimistic_write'});
        notifyLockAcquired();
        await lockRelease;
      });

      await lockAcquired;

      try {
        await expect(
          relationalDataService.dataSource.transaction(async (manager) => {
            await manager.query("SET LOCAL lock_timeout = '1ms'");
            const trx = createTransactionHandle(relationalDataService.dataSource, manager);
            try {
              return await relationalDataService.event.findById('event-1', {
                ...trx,
                lock: 'pessimistic_write',
              });
            } finally {
              invalidateTransactionHandle(trx);
            }
          }),
        ).rejects.toBeInstanceOf(QueryFailedError);
      } finally {
        releaseLock();
        await lockHolder;
      }
    });
  });

  describe('update', () => {
    it('should update event correctly', async () => {
      // First insert test data
      const event = {
        id: 'event-1',
        name: 'Test Event',
        currencyId: 'currency-1',
        pinCode: '1234',
        version: 1,
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
        deletedAt: null,
      };

      await relationalDataService.event.insert(event);

      // Update the event
      const newDeletedAt = new Date('2023-01-02T00:00:00Z');
      const newUpdatedAt = new Date('2023-01-02T00:00:00Z');

      const [, queryDetails] = await relationalDataService.event.update('event-1', {
        deletedAt: newDeletedAt,
        updatedAt: newUpdatedAt,
      });

      // Verify the data was actually updated
      const [updatedEvent] = await relationalDataService.event.findById('event-1');

      expect(updatedEvent).toMatchObject({
        ...event,
        deletedAt: newDeletedAt,
        updatedAt: newUpdatedAt,
      });
      expect(queryDetails).toMatchSnapshot();
    });

    it('should work within transaction', async () => {
      // First insert test data
      const event = {
        id: 'event-1',
        name: 'Test Event',
        currencyId: 'currency-1',
        pinCode: '1234',
        version: 1,
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
        deletedAt: null,
      };

      await relationalDataService.event.insert(event);

      const newDeletedAt = new Date('2023-01-02T00:00:00Z');
      const newUpdatedAt = new Date('2023-01-02T00:00:00Z');

      // Update inside a transaction
      await relationalDataService.transaction(async (trx) => {
        const [, queryDetails] = await relationalDataService.event.update(
          'event-1',
          {
            deletedAt: newDeletedAt,
            updatedAt: newUpdatedAt,
          },
          trx,
        );

        expect(queryDetails.queryString).toContain('UPDATE');
        const [updatedInsideTransaction] = await relationalDataService.event.findById('event-1', trx);
        expect(updatedInsideTransaction).toMatchObject({deletedAt: newDeletedAt, updatedAt: newUpdatedAt});
      });

      // Verify the changes were applied
      const [updatedEvent] = await relationalDataService.event.findById('event-1');
      expect(updatedEvent).toMatchObject({
        ...event,
        deletedAt: newDeletedAt,
        updatedAt: newUpdatedAt,
      });
    });

    it('discards the update when the transaction throws', async () => {
      const event = {
        id: 'event-1',
        name: 'Test Event',
        currencyId: 'currency-1',
        pinCode: '1234',
        version: 1,
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
        deletedAt: null,
      };
      await relationalDataService.event.insert(event);

      await expect(
        relationalDataService.transaction(async (trx) => {
          await relationalDataService.event.update('event-1', {deletedAt: new Date('2023-01-02T00:00:00Z')}, trx);
          throw new Error('boom');
        }),
      ).rejects.toThrow('boom');
      const [found] = await relationalDataService.event.findById('event-1');

      expect(found?.deletedAt).toBeNull();
    });

    it('rolls back writes made through separate repositories with the same handle', async () => {
      const event = {
        id: 'event-1',
        name: 'Test Event',
        currencyId: 'currency-1',
        pinCode: '1234',
        version: 1,
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
        deletedAt: null,
      };
      await relationalDataService.event.insert(event);

      await expect(
        relationalDataService.transaction(async (trx) => {
          await relationalDataService.event.update('event-1', {deletedAt: new Date('2023-01-02T00:00:00Z')}, trx);
          await relationalDataService.userInfo.insert(
            {
              id: 'user-1',
              eventId: 'event-1',
              name: 'Test User',
              createdAt: new Date('2023-01-01T00:00:00Z'),
              updatedAt: new Date('2023-01-01T00:00:00Z'),
            },
            trx,
          );
          throw new Error('rollback both repositories');
        }),
      ).rejects.toThrow('rollback both repositories');

      const [foundEvent] = await relationalDataService.event.findById('event-1');
      const [users] = await relationalDataService.userInfo.findByEventId('event-1');
      expect(foundEvent?.deletedAt).toBeNull();
      expect(users).toHaveLength(0);
    });
  });

  describe('findAll', () => {
    it('should find all events with limit', async () => {
      const events = [
        {
          id: 'event-1',
          name: 'Test Event 1',
          currencyId: 'currency-1',
          pinCode: '1234',
          version: 1,
          createdAt: new Date('2023-01-01T00:00:00Z'),
          updatedAt: new Date('2023-01-01T00:00:00Z'),
          deletedAt: null,
        },
        {
          id: 'event-2',
          name: 'Test Event 2',
          currencyId: 'currency-1',
          pinCode: '5678',
          version: 1,
          createdAt: new Date('2023-01-02T00:00:00Z'),
          updatedAt: new Date('2023-01-02T00:00:00Z'),
          deletedAt: null,
        },
      ];

      await relationalDataService.event.insert(events);

      const [result, queryDetails] = await relationalDataService.event.findAll({limit: 10});

      expect(result).toMatchObject(events);
      expect(queryDetails).toMatchSnapshot();
    });

    it('should respect limit parameter', async () => {
      const events = [
        {
          id: 'event-1',
          name: 'Test Event 1',
          currencyId: 'currency-1',
          pinCode: '1234',
          version: 1,
          createdAt: new Date('2023-01-01T00:00:00Z'),
          updatedAt: new Date('2023-01-01T00:00:00Z'),
          deletedAt: null,
        },
        {
          id: 'event-2',
          name: 'Test Event 2',
          currencyId: 'currency-1',
          pinCode: '5678',
          version: 1,
          createdAt: new Date('2023-01-02T00:00:00Z'),
          updatedAt: new Date('2023-01-02T00:00:00Z'),
          deletedAt: null,
        },
        {
          id: 'event-3',
          name: 'Test Event 3',
          currencyId: 'currency-1',
          pinCode: '9012',
          version: 1,
          createdAt: new Date('2023-01-03T00:00:00Z'),
          updatedAt: new Date('2023-01-03T00:00:00Z'),
          deletedAt: null,
        },
      ];

      await relationalDataService.event.insert(events);
      const [result, queryDetails] = await relationalDataService.event.findAll({limit: 2});

      expect(result).toHaveLength(2);
      expect(queryDetails).toMatchSnapshot();
    });
  });
});
