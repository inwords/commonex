import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {truncateAllTables} from '#test-support/db';

describe('UserInfoRepository', () => {
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
    it('should insert single user info correctly', async () => {
      const userInfo = {
        id: 'user-info-1',
        name: 'John Doe',
        eventId: 'event-1',
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
      };

      const [, queryDetails] = await relationalDataService.userInfo.insert(userInfo);
      const [foundUserInfos] = await relationalDataService.userInfo.findByEventId('event-1');

      expect(foundUserInfos).toMatchObject([userInfo]);
      expect(queryDetails).toMatchSnapshot();
    });

    it('should insert multiple user infos correctly', async () => {
      const userInfos = [
        {
          id: 'user-info-1',
          name: 'John Doe',
          eventId: 'event-1',
          createdAt: new Date('2023-01-01T00:00:00Z'),
          updatedAt: new Date('2023-01-01T00:00:00Z'),
        },
        {
          id: 'user-info-2',
          name: 'Jane Smith',
          eventId: 'event-1',
          createdAt: new Date('2023-01-01T00:00:00Z'),
          updatedAt: new Date('2023-01-01T00:00:00Z'),
        },
      ];

      const [, queryDetails] = await relationalDataService.userInfo.insert(userInfos);
      const [foundUserInfos] = await relationalDataService.userInfo.findByEventId('event-1');

      expect(foundUserInfos).toMatchObject(userInfos);
      expect(queryDetails).toMatchSnapshot();
    });
  });

  describe('findByEventId', () => {
    it('should find user infos by event id', async () => {
      // First insert test data
      const userInfos = [
        {
          id: 'user-info-1',
          name: 'John Doe',
          eventId: 'event-1',
          createdAt: new Date('2023-01-01T00:00:00Z'),
          updatedAt: new Date('2023-01-01T00:00:00Z'),
        },
        {
          id: 'user-info-2',
          name: 'Jane Smith',
          eventId: 'event-1',
          createdAt: new Date('2023-01-01T00:00:00Z'),
          updatedAt: new Date('2023-01-01T00:00:00Z'),
        },
      ];

      await relationalDataService.userInfo.insert(userInfos);

      // Now look up user info by event id
      const [result, queryDetails] = await relationalDataService.userInfo.findByEventId('event-1');

      expect(result).toMatchObject(userInfos);
      expect(queryDetails).toMatchSnapshot();
    });

    it('should return empty array for non-existent event', async () => {
      const [result, queryDetails] = await relationalDataService.userInfo.findByEventId('non-existent');

      expect(result).toEqual([]);
      expect(queryDetails).toMatchSnapshot();
    });
  });

  describe('findAll', () => {
    it('should find all user infos with limit', async () => {
      const userInfos = [
        {
          id: 'user-info-1',
          name: 'John Doe',
          eventId: 'event-1',
          createdAt: new Date('2023-01-01T00:00:00Z'),
          updatedAt: new Date('2023-01-01T00:00:00Z'),
        },
        {
          id: 'user-info-2',
          name: 'Jane Smith',
          eventId: 'event-2',
          createdAt: new Date('2023-01-02T00:00:00Z'),
          updatedAt: new Date('2023-01-02T00:00:00Z'),
        },
      ];

      await relationalDataService.userInfo.insert(userInfos);

      const [result, queryDetails] = await relationalDataService.userInfo.findAll({limit: 10});

      expect(result).toMatchObject(userInfos);
      expect(queryDetails).toMatchSnapshot();
    });

    it('should respect limit parameter', async () => {
      const userInfos = [
        {
          id: 'user-info-1',
          name: 'John Doe',
          eventId: 'event-1',
          createdAt: new Date('2023-01-01T00:00:00Z'),
          updatedAt: new Date('2023-01-01T00:00:00Z'),
        },
        {
          id: 'user-info-2',
          name: 'Jane Smith',
          eventId: 'event-1',
          createdAt: new Date('2023-01-02T00:00:00Z'),
          updatedAt: new Date('2023-01-02T00:00:00Z'),
        },
        {
          id: 'user-info-3',
          name: 'Bob Johnson',
          eventId: 'event-1',
          createdAt: new Date('2023-01-03T00:00:00Z'),
          updatedAt: new Date('2023-01-03T00:00:00Z'),
        },
      ];

      await relationalDataService.userInfo.insert(userInfos);

      const [result, queryDetails] = await relationalDataService.userInfo.findAll({limit: 2});

      expect(result).toHaveLength(2);
      expect(queryDetails).toMatchSnapshot();
    });
  });
});
