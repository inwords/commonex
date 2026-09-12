import {DataSource} from 'typeorm';

import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

export const createTestRelationalDataService = ({
  showQueryDetails = false,
}: {showQueryDetails?: boolean} = {}): RelationalDataService =>
  new RelationalDataService({dbConfig: appDbConfig, showQueryDetails});

/** Empties every mapped table. Test-only; production code never truncates. */
export const truncateAllTables = async (dataSource: DataSource): Promise<void> => {
  const tableNames = dataSource.entityMetadatas.map((metadata) => metadata.tableName).join(', ');

  await dataSource.query(`TRUNCATE ${tableNames} RESTART IDENTITY CASCADE;`);
};
