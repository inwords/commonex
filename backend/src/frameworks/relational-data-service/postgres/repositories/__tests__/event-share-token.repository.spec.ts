import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {truncateAllTables} from '#test-support/db';
import {useFakeTimers} from '#test-support/relational-state';

describe('EventShareTokenRepository', () => {
  let relationalDataService: RelationalDataService;

  beforeAll(async () => {
    useFakeTimers();

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
    jest.useRealTimers();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
  });

  describe('insert', () => {
    it('should insert event share token correctly', async () => {
      const eventShareToken = {
        token: 'test-token-123',
        eventId: 'event-1',
        expiresAt: new Date('2025-01-01T00:00:00Z'),
        createdAt: new Date('2023-01-01T00:00:00Z'),
      };

      const [, queryDetails] = await relationalDataService.eventShareToken.insert(eventShareToken);
      const [result] = await relationalDataService.eventShareToken.findByToken('test-token-123');

      expect(result).toMatchObject(eventShareToken);
      expect(queryDetails).toMatchSnapshot();
    });
  });

  describe('findByToken', () => {
    it('should find event share token by token', async () => {
      const futureDate = new Date();
      futureDate.setFullYear(futureDate.getFullYear() + 1);

      const eventShareToken = {
        token: 'test-token-123',
        eventId: 'event-1',
        expiresAt: futureDate,
        createdAt: new Date('2023-01-01T00:00:00Z'),
      };

      await relationalDataService.eventShareToken.insert(eventShareToken);

      const [result, queryDetails] = await relationalDataService.eventShareToken.findByToken('test-token-123');

      expect(result).toMatchObject(eventShareToken);
      expect(queryDetails).toMatchSnapshot();
    });

    it('should return null for non-existent token', async () => {
      const [result, queryDetails] = await relationalDataService.eventShareToken.findByToken('non-existent-token');

      expect(result).toBeNull();
      expect(queryDetails).toMatchSnapshot();
    });
  });

  describe('findByEventId', () => {
    it('should limit the active token lookup to one database row', async () => {
      const [, queryDetails] = await relationalDataService.eventShareToken.findByEventId('event-1');

      expect(queryDetails.queryString).toContain('LIMIT 1');
    });

    it('should find active token by event id', async () => {
      const futureDate = new Date();
      futureDate.setFullYear(futureDate.getFullYear() + 1);

      const eventShareToken = {
        token: 'test-token-123',
        eventId: 'event-1',
        expiresAt: futureDate,
        createdAt: new Date('2023-01-01T00:00:00Z'),
      };

      await relationalDataService.eventShareToken.insert(eventShareToken);

      const [result, queryDetails] = await relationalDataService.eventShareToken.findByEventId('event-1');

      expect(result).toMatchObject(eventShareToken);
      expect(queryDetails).toMatchSnapshot();
    });

    it('returns the expired token for replacement', async () => {
      const pastDate = new Date();
      pastDate.setFullYear(pastDate.getFullYear() - 1);

      const eventShareToken = {
        token: 'test-token-123',
        eventId: 'event-1',
        expiresAt: pastDate,
        createdAt: new Date('2023-01-01T00:00:00Z'),
      };

      await relationalDataService.eventShareToken.insert(eventShareToken);

      const [result, queryDetails] = await relationalDataService.eventShareToken.findByEventId('event-1');

      expect(result).toMatchObject(eventShareToken);
      expect(queryDetails).toMatchSnapshot();
    });

    it('rejects a second token for the same event', async () => {
      const token = {
        token: 'first-token',
        eventId: 'event-1',
        expiresAt: new Date('2025-01-01T00:00:00Z'),
        createdAt: new Date('2023-01-01T00:00:00Z'),
      };
      await relationalDataService.eventShareToken.insert(token);

      await expect(
        relationalDataService.eventShareToken.insert({...token, token: 'second-token'}),
      ).rejects.toMatchObject({driverError: {code: '23505'}});
      const [remaining] = await relationalDataService.eventShareToken.findAll({limit: 10});

      expect(remaining).toEqual([token]);
    });

    it('should return null for non-existent event', async () => {
      const [result, queryDetails] = await relationalDataService.eventShareToken.findByEventId('non-existent');

      expect(result).toBeNull();
      expect(queryDetails).toMatchSnapshot();
    });
  });

  describe('findAll', () => {
    it('should find all event share tokens with limit', async () => {
      const futureDate = new Date();
      futureDate.setFullYear(futureDate.getFullYear() + 1);

      const eventShareTokens = [
        {
          token: 'test-token-1',
          eventId: 'event-1',
          expiresAt: futureDate,
          createdAt: new Date('2023-01-01T00:00:00Z'),
        },
        {
          token: 'test-token-2',
          eventId: 'event-2',
          expiresAt: futureDate,
          createdAt: new Date('2023-01-02T00:00:00Z'),
        },
      ];

      await relationalDataService.eventShareToken.insert(eventShareTokens);

      const [result, queryDetails] = await relationalDataService.eventShareToken.findAll({limit: 10});

      expect(result).toMatchObject(eventShareTokens);
      expect(queryDetails).toMatchSnapshot();
    });

    it('should respect limit parameter', async () => {
      const futureDate = new Date();
      futureDate.setFullYear(futureDate.getFullYear() + 1);

      const eventShareTokens = [
        {
          token: 'test-token-1',
          eventId: 'event-1',
          expiresAt: futureDate,
          createdAt: new Date('2023-01-01T00:00:00Z'),
        },
        {
          token: 'test-token-2',
          eventId: 'event-2',
          expiresAt: futureDate,
          createdAt: new Date('2023-01-02T00:00:00Z'),
        },
        {
          token: 'test-token-3',
          eventId: 'event-3',
          expiresAt: futureDate,
          createdAt: new Date('2023-01-03T00:00:00Z'),
        },
      ];

      await relationalDataService.eventShareToken.insert(eventShareTokens);

      const [result, queryDetails] = await relationalDataService.eventShareToken.findAll({limit: 2});

      expect(result).toHaveLength(2);
      expect(queryDetails).toMatchSnapshot();
    });
  });

  describe('deleteByToken', () => {
    it('should delete event share token by token', async () => {
      const futureDate = new Date();
      futureDate.setFullYear(futureDate.getFullYear() + 1);

      const eventShareToken = {
        token: 'test-token-123',
        eventId: 'event-1',
        expiresAt: futureDate,
        createdAt: new Date('2023-01-01T00:00:00Z'),
      };

      await relationalDataService.eventShareToken.insert(eventShareToken);

      const [, queryDetails] = await relationalDataService.eventShareToken.deleteByToken('test-token-123');

      const [result] = await relationalDataService.eventShareToken.findByToken('test-token-123');

      expect(result).toBeNull();
      expect(queryDetails).toMatchSnapshot();
    });
  });

  describe('deleteExpiredBatch', () => {
    it('should delete at most one ordered batch of expired tokens', async () => {
      await relationalDataService.eventShareToken.insert([
        {
          token: 'expired-1',
          eventId: 'event-1',
          expiresAt: new Date('2023-01-01T00:00:00Z'),
          createdAt: new Date('2022-01-01T00:00:00Z'),
        },
        {
          token: 'expired-2',
          eventId: 'event-2',
          expiresAt: new Date('2023-01-02T00:00:00Z'),
          createdAt: new Date('2022-01-01T00:00:00Z'),
        },
        {
          token: 'expired-3',
          eventId: 'event-3',
          expiresAt: new Date('2023-01-03T00:00:00Z'),
          createdAt: new Date('2022-01-01T00:00:00Z'),
        },
        {
          token: 'live',
          eventId: 'event-4',
          expiresAt: new Date('2025-01-01T00:00:00Z'),
          createdAt: new Date('2022-01-01T00:00:00Z'),
        },
      ]);

      const [deletedCount, queryDetails] = await relationalDataService.eventShareToken.deleteExpiredBatch({
        expiresBefore: new Date('2024-01-01T00:00:00Z'),
        limit: 2,
      });
      const [remaining] = await relationalDataService.eventShareToken.findAll({limit: 10});

      expect(deletedCount).toBe(2);
      expect(remaining.map((record) => record.token).sort()).toEqual(['expired-3', 'live']);
      expect(queryDetails).toMatchSnapshot();
    });
  });
});
