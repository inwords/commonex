import {DataSource, EntityManager} from 'typeorm';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {ITransaction, ITransactionIsolationLevel} from '#domain/abstracts/relational-data-service/types';

import {CurrencyRateRepository} from '#frameworks/relational-data-service/postgres/repositories/currency-rate.repository';
import {CurrencyRepository} from '#frameworks/relational-data-service/postgres/repositories/currency.repository';
import {EventShareTokenRepository} from '#frameworks/relational-data-service/postgres/repositories/event-share-token.repository';
import {EventRepository} from '#frameworks/relational-data-service/postgres/repositories/event.repository';
import {ExpenseRepository} from '#frameworks/relational-data-service/postgres/repositories/expense.repository';
import {IdempotencyKeyRepository} from '#frameworks/relational-data-service/postgres/repositories/idempotency-key.repository';
import {UserInfoRepository} from '#frameworks/relational-data-service/postgres/repositories/user-info.repository';

import {DbConfig, createTypeormConfigDefault} from './config';
import {BannerRepository} from './repositories/banner.repository';
import {createTransactionHandle, invalidateTransactionHandle, requireTransactionManager} from './transaction-context';

export class RelationalDataService implements RelationalDataServiceAbstract {
  readonly dbConfig: DbConfig;
  readonly dataSource: DataSource;
  readonly showQueryDetails: boolean;

  readonly event: EventRepository;
  readonly banner: BannerRepository;
  readonly userInfo: UserInfoRepository;
  readonly currency: CurrencyRepository;
  readonly expense: ExpenseRepository;
  readonly currencyRate: CurrencyRateRepository;
  readonly eventShareToken: EventShareTokenRepository;
  readonly idempotencyKey: IdempotencyKeyRepository;

  constructor({dbConfig, showQueryDetails}: {dbConfig: DbConfig; showQueryDetails: boolean}) {
    this.dbConfig = dbConfig;
    this.dataSource = new DataSource(createTypeormConfigDefault(dbConfig));
    this.showQueryDetails = showQueryDetails;
    this.banner = new BannerRepository(this.dataSource, showQueryDetails);

    this.event = new EventRepository({dataSource: this.dataSource, showQueryDetails});
    this.userInfo = new UserInfoRepository({dataSource: this.dataSource, showQueryDetails});
    this.currency = new CurrencyRepository({dataSource: this.dataSource, showQueryDetails});
    this.expense = new ExpenseRepository({dataSource: this.dataSource, showQueryDetails});
    this.currencyRate = new CurrencyRateRepository({dataSource: this.dataSource, showQueryDetails});
    this.eventShareToken = new EventShareTokenRepository({dataSource: this.dataSource, showQueryDetails});
    this.idempotencyKey = new IdempotencyKeyRepository({dataSource: this.dataSource, showQueryDetails});
  }

  transaction<T>(runInTransaction: (trx: ITransaction) => Promise<T>): Promise<T>;
  transaction<T>(
    isolationLevel: ITransactionIsolationLevel,
    runInTransaction: (trx: ITransaction) => Promise<T>,
  ): Promise<T>;
  transaction<T>(
    isolationLevelOrCallback: ITransactionIsolationLevel | ((trx: ITransaction) => Promise<T>),
    maybeCallback?: (trx: ITransaction) => Promise<T>,
  ): Promise<T> {
    const runInTransaction = typeof isolationLevelOrCallback === 'function' ? isolationLevelOrCallback : maybeCallback;
    if (runInTransaction == null) {
      throw new TypeError('A transaction callback is required');
    }

    const runWithHandle = async (manager: EntityManager): Promise<T> => {
      const trx = createTransactionHandle(this.dataSource, manager);
      try {
        return await runInTransaction(trx);
      } finally {
        invalidateTransactionHandle(trx);
      }
    };

    if (typeof isolationLevelOrCallback === 'function') {
      return this.dataSource.transaction(runWithHandle);
    }

    return this.dataSource.transaction(isolationLevelOrCallback, runWithHandle);
  }

  async tryAcquireTransactionLock(key: string, trx: ITransaction): Promise<boolean> {
    const manager = requireTransactionManager(this.dataSource, trx);
    const rows = await manager.query<{acquired: boolean}[]>(
      'SELECT pg_try_advisory_xact_lock(hashtextextended($1, 0)) AS acquired',
      [key],
    );

    return rows[0]?.acquired === true;
  }

  async initialize(): Promise<void> {
    await this.dataSource.initialize();
    await this.setupSearchPathAndValidate();
  }

  async destroy(): Promise<void> {
    if (this.dataSource.isInitialized) {
      await this.dataSource.destroy();
    }
  }

  async healthCheck(): Promise<void> {
    if (!this.dataSource.isInitialized) {
      throw new Error('Database is not initialized');
    }

    // Simple query to verify database connectivity
    await this.dataSource.query('SELECT 1');
  }

  private async setupSearchPathAndValidate(): Promise<void> {
    const user = this.dbConfig.user;
    const schema = this.dbConfig.schema;

    const [{current_schema: currentSchema}] =
      await this.dataSource.query<[{current_schema: string}]>(`SELECT current_schema();`);
    if (currentSchema !== schema) {
      throw new Error(`Invalid connection schema for user "${user}". Expected "${schema}", got "${currentSchema}";`);
    }
  }
}
