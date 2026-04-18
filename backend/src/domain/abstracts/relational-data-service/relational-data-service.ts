import {IRelationalDataService} from '#domain/abstracts/relational-data-service/types';
import {EventRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/event.repository';
import {UserInfoRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/user-info.repository';
import {CurrencyRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/currency.repository';
import {ExpenseRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/expense.repository';
import {CurrencyRateRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/currency-rate.repository';
import {EventShareTokenRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/event-share-token.repository';
import {IdempotencyKeyRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/idempotency-key.repository';

export abstract class RelationalDataServiceAbstract implements IRelationalDataService {
  abstract event: EventRepositoryAbstract;
  abstract userInfo: UserInfoRepositoryAbstract;
  abstract currency: CurrencyRepositoryAbstract;
  abstract expense: ExpenseRepositoryAbstract;
  abstract currencyRate: CurrencyRateRepositoryAbstract;
  abstract eventShareToken: EventShareTokenRepositoryAbstract;
  abstract idempotencyKey: IdempotencyKeyRepositoryAbstract;

  abstract initialize: IRelationalDataService['initialize'];
  abstract transaction: IRelationalDataService['transaction'];
  abstract destroy: IRelationalDataService['destroy'];
  abstract flush: IRelationalDataService['flush'];

  abstract healthCheck: IRelationalDataService['healthCheck'];
}
