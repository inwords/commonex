import {CurrencyRateRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/currency-rate.repository';
import {CurrencyRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/currency.repository';
import {EventShareTokenRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/event-share-token.repository';
import {EventRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/event.repository';
import {ExpenseRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/expense.repository';
import {IdempotencyKeyRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/idempotency-key.repository';
import {UserInfoRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/user-info.repository';
import {IRelationalDataService} from '#domain/abstracts/relational-data-service/types';

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

  abstract healthCheck: IRelationalDataService['healthCheck'];
}
