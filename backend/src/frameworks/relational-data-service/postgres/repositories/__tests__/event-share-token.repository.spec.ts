import {useFakeTimers} from '#usecases/__tests__/test-helpers';

import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

describe('EventShareTokenRepository', () => {
  let relationalDataService: RelationalDataService;

  beforeAll(async () => {
    useFakeTimers();

    relationalDataService = new RelationalDataService({
      dbConfig: appDbConfig,
      showQueryDetails: true,
    });
    await relationalDataService.initialize();
    // Очищаем базу данных перед запуском тестов
    await relationalDataService.flush();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
    jest.useRealTimers();
  });

  beforeEach(async () => {
    await relationalDataService.flush();
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

  describe('findOneActiveByEventId', () => {
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

      const [result, queryDetails] = await relationalDataService.eventShareToken.findOneActiveByEventId('event-1');

      expect(result).toMatchObject(eventShareToken);
      expect(queryDetails).toMatchSnapshot();
    });

    it('should return null for expired token', async () => {
      const pastDate = new Date();
      pastDate.setFullYear(pastDate.getFullYear() - 1);

      const eventShareToken = {
        token: 'test-token-123',
        eventId: 'event-1',
        expiresAt: pastDate,
        createdAt: new Date('2023-01-01T00:00:00Z'),
      };

      await relationalDataService.eventShareToken.insert(eventShareToken);

      const [result, queryDetails] = await relationalDataService.eventShareToken.findOneActiveByEventId('event-1');

      expect(result).toBeNull();
      expect(queryDetails).toMatchSnapshot();
    });

    it('should return most recent active token when multiple exist', async () => {
      const futureDate1 = new Date();
      futureDate1.setFullYear(futureDate1.getFullYear() + 1);

      const futureDate2 = new Date();
      futureDate2.setFullYear(futureDate2.getFullYear() + 2);

      const eventShareToken1 = {
        token: 'test-token-1',
        eventId: 'event-1',
        expiresAt: futureDate1,
        createdAt: new Date('2023-01-01T00:00:00Z'),
      };

      const eventShareToken2 = {
        token: 'test-token-2',
        eventId: 'event-1',
        expiresAt: futureDate2,
        createdAt: new Date('2023-01-02T00:00:00Z'),
      };

      await relationalDataService.eventShareToken.insert(eventShareToken1);
      await relationalDataService.eventShareToken.insert(eventShareToken2);

      const [result, queryDetails] = await relationalDataService.eventShareToken.findOneActiveByEventId('event-1');

      expect(result).toMatchObject(eventShareToken2);
      expect(queryDetails).toMatchSnapshot();
    });

    it('should return null for non-existent event', async () => {
      const [result, queryDetails] = await relationalDataService.eventShareToken.findOneActiveByEventId('non-existent');

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
});
