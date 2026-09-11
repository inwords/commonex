import 'tsconfig-paths/register';

import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

void (async (): Promise<void> => {
  const dataService = new RelationalDataService({
    showQueryDetails: false,
    dbConfig: {
      ...appDbConfig,
      logging: true,
    },
  });

  await dataService.initialize();

  await dataService.dataSource.runMigrations({
    transaction: 'each',
  });
  await dataService.destroy();
})();
