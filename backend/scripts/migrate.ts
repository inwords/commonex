import 'tsconfig-paths/register';

import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {runMigrations} from './run-migrations';

void (async (): Promise<void> => {
  const dataService = new RelationalDataService({
    showQueryDetails: false,
    dbConfig: {
      ...appDbConfig,
      logging: true,
    },
  });

  try {
    await dataService.initialize();
    await runMigrations(dataService.dataSource);
  } finally {
    // Closing the pool also releases the session lock if explicit unlock fails.
    await dataService.destroy();
  }
})();
