import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {DataSource} from 'typeorm';
import {createTypeormConfigDefault, DbConfig} from './config';
import {EventRepository} from '#frameworks/relational-data-service/postgres/repositories/event.repository';
import {UserInfoRepository} from '#frameworks/relational-data-service/postgres/repositories/user-info.repository';
import {CurrencyRepository} from '#frameworks/relational-data-service/postgres/repositories/currency.repository';
import {ExpenseRepository} from '#frameworks/relational-data-service/postgres/repositories/expense.repository';
import {CurrencyRateRepository} from '#frameworks/relational-data-service/postgres/repositories/currency-rate.repository';
import {EventShareTokenRepository} from '#frameworks/relational-data-service/postgres/repositories/event-share-token.repository';
import {IdempotencyKeyRepository} from '#frameworks/relational-data-service/postgres/repositories/idempotency-key.repository';

export class RelationalDataService implements RelationalDataServiceAbstract {
  readonly dbConfig: DbConfig;
  readonly dataSource: DataSource;
  readonly showQueryDetails: boolean;
  readonly transaction;

  readonly event: EventRepository;
  readonly userInfo: UserInfoRepository;
  readonly currency: CurrencyRepository;
  readonly expense: ExpenseRepository;
  readonly currencyRate: CurrencyRateRepository;
  readonly eventShareToken: EventShareTokenRepository;
  readonly idempotencyKey: IdempotencyKeyRepository;

  constructor({dbConfig, showQueryDetails}: {dbConfig: DbConfig; showQueryDetails: boolean}) {
    this.dbConfig = dbConfig;
    this.dataSource = new DataSource(createTypeormConfigDefault(dbConfig));
    this.transaction = this.dataSource.transaction.bind(this.dataSource);
    this.showQueryDetails = showQueryDetails;

    this.event = new EventRepository({dataSource: this.dataSource, showQueryDetails});
    this.userInfo = new UserInfoRepository({dataSource: this.dataSource, showQueryDetails});
    this.currency = new CurrencyRepository({dataSource: this.dataSource, showQueryDetails});
    this.expense = new ExpenseRepository({dataSource: this.dataSource, showQueryDetails});
    this.currencyRate = new CurrencyRateRepository({dataSource: this.dataSource, showQueryDetails});
    this.eventShareToken = new EventShareTokenRepository({dataSource: this.dataSource, showQueryDetails});
    this.idempotencyKey = new IdempotencyKeyRepository({dataSource: this.dataSource, showQueryDetails});
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

  async flush(): Promise<void> {
    const targetEntities = this.dataSource.entityMetadatas;

    const tableNames = targetEntities.map((t) => t.tableName);
    const tableNamesJoined = tableNames.join(', ');
    const truncateSql = `TRUNCATE ${tableNamesJoined} RESTART IDENTITY CASCADE;`;
    await this.dataSource.query(truncateSql);
  }

  async healthCheck(): Promise<void> {
    if (!this.dataSource || !this.dataSource.isInitialized) {
      throw new Error('Database is not initialized');
    }

    // Simple query to verify database connectivity
    await this.dataSource.query('SELECT 1');
  }

  private async setupSearchPathAndValidate(): Promise<void | never> {
    const user = this.dbConfig.user;
    const schema = this.dbConfig.schema;

    const [{current_schema: currentSchema}] = await this.dataSource.query(`SELECT current_schema();`);
    if (currentSchema !== schema) {
      throw new Error(`Invalid connection schema for user "${user}". Expected "${schema}", got "${currentSchema}";`);
    }
  }
}
